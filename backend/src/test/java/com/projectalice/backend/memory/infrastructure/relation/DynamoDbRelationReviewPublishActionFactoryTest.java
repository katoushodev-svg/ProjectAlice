package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class DynamoDbRelationReviewPublishActionFactoryTest {
    private static final String TABLE = "memory";
    private final DynamoDbClient client = mock(DynamoDbClient.class);
    private final DynamoDbRelationControlRepository controls = mock(DynamoDbRelationControlRepository.class);
    private final DynamoDbRelationReviewStageStore stageStore = new DynamoDbRelationReviewStageStore(client, TABLE,
            new HmacRelationTargetSetDigester(() -> new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256")));
    private final DynamoDbRelationReviewPublishActionFactory factory = new DynamoDbRelationReviewPublishActionFactory(
            TABLE, controls, stageStore, Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC));

    @Test void directActionsSatisfyBuilderAndContainCiphertextAndBinding() {
        var draft = draft(1);
        when(controls.readControls()).thenReturn(controls(0, 0));
        var encrypted = new RelationCiphertext(1, 1, new byte[12], new byte[16]);
        var target = new RelationReviewStageStore.EncryptedTarget(0,
                draft.targets().get(0).snapshot().memoryId().value(), 1, "UPDATE_TARGET", encrypted);
        var actions = factory.directActions(draft, uuid(500), 1, 1, "D".repeat(43),
                OffsetDateTime.parse("2026-10-29T10:30:00Z"), encrypted, List.of(target), binding());

        var request = new DynamoDbRelationReviewPublishRequestBuilder().buildDirect(draft, actions);
        assertEquals(8, request.transactItems().size());
        assertTrue(request.transactItems().stream().anyMatch(item -> item.put() != null
                && item.put().item().containsKey("ciphertext")));
    }

    @Test void stagedActionsSatisfyBuilderWithPreparingReferenceAndDigestBoundReviewUpdate() {
        var draft = draft(19);
        when(controls.readControls()).thenReturn(controls(0, 1));
        String reviewId = uuid(501);
        String digest = "E".repeat(43);
        var actions = factory.stagedActions(draft, reviewId, 1, 1, digest, "stage-owner",
                OffsetDateTime.parse("2026-10-29T10:30:00Z"), binding());

        var request = new DynamoDbRelationReviewPublishRequestBuilder().buildStaged(draft, actions, 19, digest);
        assertEquals(6, request.transactItems().size());
        assertTrue(request.transactItems().stream().noneMatch(item -> item.put() != null
                && item.put().item().containsKey("ciphertext")));
    }

    private static DynamoDbRelationControlRepository.Controls controls(long activeCount, long preparingCount) {
        return new DynamoDbRelationControlRepository.Controls(new RelationGlobalControl((int) activeCount, 20, 1),
                new RelationKeyControl(1, 2, 2, List.of(new RelationKeyControl.KeyVersion(1,
                        RelationKeyControl.KeyState.WRITE_ACTIVE, "AAAAAA", 0, activeCount, preparingCount, 0))));
    }

    private static RelationReviewDraft draft(int count) {
        List<RelationReviewDraft.Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var id = new MemoryId(uuid(i + 1));
            var snapshot = new RelatedMemorySearchResult.Candidate(id, i + 1L, new MemoryContent("old " + i),
                    MemoryCategory.PREFERENCE, CaptureType.EXPLICIT, SensitivityLevel.NORMAL, MemoryState.ACTIVE);
            var preview = new RelationMutationPreview(RelationMutationPreview.Operation.UPDATE_TARGET,
                    RelationType.UNCERTAIN, id, i + 1L, new MemoryContent("new value"),
                    MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL);
            targets.add(new RelationReviewDraft.Target(snapshot, preview));
        }
        return new RelationReviewDraft(RelationType.UNCERTAIN, new MemoryContent("new value"),
                MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL, targets);
    }

    private static MemoryIdempotencyBinding binding() {
        var now = OffsetDateTime.parse("2026-10-29T10:00:00Z");
        return new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", "A".repeat(43)),
                "B".repeat(43), java.util.UUID.randomUUID(), MemoryIdempotencyBinding.Status.PROCESSING,
                1, "owner", now.plusMinutes(2), null, null, now, now, null);
    }

    private static String uuid(int n) {
        return String.format(java.util.Locale.ROOT, "00000000-0000-4000-8000-%012d", n);
    }
}
