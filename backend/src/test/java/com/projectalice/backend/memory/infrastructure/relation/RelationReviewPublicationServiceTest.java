package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.projectalice.backend.memory.application.RelationMutationPreview;
import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.application.RelationType;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.infrastructure.idempotency.MemoryIdempotencyBinding;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

class RelationReviewPublicationServiceTest {
    private final RelationStateKeyProvider keys = new FixedKeys();
    private final RelationAead aead = new JceRelationAead(keys, new UniqueNonceProvider());
    private final RelationTargetSetDigester digester = new HmacRelationTargetSetDigester(
            () -> new SecretKeySpec(fill(32, (byte) 9), "HmacSHA256"));
    private final RelationKeyReferenceStore references = mock(RelationKeyReferenceStore.class);
    private final RelationReviewStageStore stage = mock(RelationReviewStageStore.class);
    private final DynamoDbRelationReviewPublishActionFactory actions = mock(DynamoDbRelationReviewPublishActionFactory.class);
    private final DynamoDbRelationReviewPublishRequestBuilder requestBuilder = mock(DynamoDbRelationReviewPublishRequestBuilder.class);
    private final DynamoDbRelationReviewPublishExecutor executor = mock(DynamoDbRelationReviewPublishExecutor.class);
    private final RelationReviewPublicationService service = new RelationReviewPublicationService(keys, aead,
            new RelationReviewPayloadCodec(), digester, references, stage, actions, requestBuilder, executor,
            Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC));

    @Test void smallTargetSetEncryptsAndCallsDirectAtomicExecutorWithoutStaging() {
        var draft = draft(1);
        when(executor.publishDirect(any(), any())).thenReturn(TransactWriteItemsResponse.builder().build());
        when(requestBuilder.buildDirect(any(), any())).thenReturn(software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest.builder().build());
        doReturn(List.of()).when(actions).directActions(any(), anyString(), anyLong(), anyInt(), anyString(),
                any(), any(), any(), any());

        var result = service.publish(draft, processingBinding());

        assertEquals(1, result.targetCount());
        org.junit.jupiter.api.Assertions.assertFalse(result.staged());
        verify(executor).publishDirect(any(), any());
        verify(stage, never()).stageAndVerify(anyString(), anyLong(), anyInt(), anyString(), any(), any(), anyString());
    }

    @Test void largeTargetSetStagesAndThenCallsStagedAtomicExecutor() {
        var draft = draft(19);
        when(references.createPreparingIfAbsent(any())).thenReturn(RelationKeyReferenceStore.CreateResult.CREATED);
        when(executor.publishStaged(any(), any(), anyInt(), anyString()))
                .thenReturn(TransactWriteItemsResponse.builder().build());
        when(requestBuilder.buildStaged(any(), any(), anyInt(), anyString()))
                .thenReturn(software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest.builder().build());
        doReturn(List.of()).when(actions).stagedActions(any(), anyString(), anyLong(), anyInt(), anyString(),
                anyString(), any(), any());

        var result = service.publish(draft, processingBinding());

        assertEquals(19, result.targetCount());
        org.junit.jupiter.api.Assertions.assertTrue(result.staged());
        verify(stage).createPreparingMetadata(anyString(), anyLong(), anyInt(), any(), anyString(), anyString(), any(), any());
        verify(stage).stageAndVerify(anyString(), anyLong(), anyInt(), anyString(), any(), any(), anyString());
        verify(executor).publishStaged(any(), any(), anyInt(), anyString());
    }

    @Test void failedStageCleansOnlyOwnedCiphertextAndMovesReferenceToCleanupPending() {
        when(references.createPreparingIfAbsent(any())).thenReturn(RelationKeyReferenceStore.CreateResult.CREATED);
        org.mockito.Mockito.doThrow(new RelationCryptoException()).when(stage)
                .stageAndVerify(anyString(), anyLong(), anyInt(), anyString(), any(), any(), anyString());

        assertThrows(RelationCryptoException.class, () -> service.publish(draft(19), processingBinding()));
        verify(stage).cleanupOwnedCiphertext(anyString(), anyString());
        verify(stage).markCleanupPending(anyString(), anyString(), any());
        verify(references).transition(any(), anyLong(), any(), any());
        verify(executor, never()).publishStaged(any(), any(), anyInt(), anyString());
    }

    private static RelationReviewDraft draft(int count) {
        List<RelationReviewDraft.Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuid = String.format(java.util.Locale.ROOT, "00000000-0000-4000-8000-%012d", i + 1);
            var id = new MemoryId(uuid);
            var snapshot = new RelatedMemorySearchResult.Candidate(id, i + 1L,
                    new MemoryContent("old target " + i), MemoryCategory.PREFERENCE,
                    CaptureType.EXPLICIT, SensitivityLevel.NORMAL, MemoryState.ACTIVE);
            var preview = new RelationMutationPreview(RelationMutationPreview.Operation.UPDATE_TARGET,
                    RelationType.UNCERTAIN, id, i + 1L, new MemoryContent("new proposal"),
                    MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL);
            targets.add(new RelationReviewDraft.Target(snapshot, preview));
        }
        return new RelationReviewDraft(RelationType.UNCERTAIN, new MemoryContent("new proposal"),
                MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL, targets);
    }

    private static MemoryIdempotencyBinding processingBinding() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-29T10:00:00Z");
        return new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", "A".repeat(43)),
                "B".repeat(43), java.util.UUID.randomUUID(), MemoryIdempotencyBinding.Status.PROCESSING,
                1, "owner-1", now.plusMinutes(2), null, null, now, now, null);
    }

    private static byte[] fill(int length, byte value) {
        byte[] bytes = new byte[length]; java.util.Arrays.fill(bytes, value); return bytes;
    }

    private static final class FixedKeys implements RelationStateKeyProvider {
        private final SecretKey key = new SecretKeySpec(fill(32, (byte) 3), "AES");
        @Override public void probeRelationPurpose() { }
        @Override public KeyMaterial activeEncryptionKey() { return new KeyMaterial(1, key); }
        @Override public SecretKey decryptionKey(int keyVersion) {
            if (keyVersion != 1) throw new RelationControlUnavailableException();
            return key;
        }
    }

    private static final class UniqueNonceProvider implements RelationNonceProvider {
        private final AtomicLong counter = new AtomicLong();
        @Override public byte[] reserveNonce(int keyVersion) {
            return ByteBuffer.allocate(12).putInt(keyVersion).putLong(counter.incrementAndGet()).array();
        }
    }
}
