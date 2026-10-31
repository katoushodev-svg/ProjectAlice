package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.infrastructure.idempotency.MemoryIdempotencyBinding;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

/**
 * Connects a validated in-memory Review draft to AEAD, optional durable staging and the atomic
 * DynamoDB publish executor. The caller must already own a PROCESSING Idempotency Binding.
 */
public final class RelationReviewPublicationService {
    public static final int DIRECT_TARGET_LIMIT = 18;
    private static final ZoneOffset PROJECT_TIMEZONE = ZoneOffset.ofHours(9);
    private final RelationStateKeyProvider keyProvider;
    private final RelationAead aead;
    private final RelationReviewPayloadCodec codec;
    private final RelationTargetSetDigester targetSetDigester;
    private final RelationKeyReferenceStore keyReferences;
    private final RelationReviewStageStore stageStore;
    private final DynamoDbRelationReviewPublishActionFactory actionFactory;
    private final DynamoDbRelationReviewPublishRequestBuilder requestBuilder;
    private final DynamoDbRelationReviewPublishExecutor executor;
    private final Clock clock;

    public RelationReviewPublicationService(RelationStateKeyProvider keyProvider, RelationAead aead,
            RelationReviewPayloadCodec codec, RelationTargetSetDigester targetSetDigester,
            RelationKeyReferenceStore keyReferences, RelationReviewStageStore stageStore,
            DynamoDbRelationReviewPublishActionFactory actionFactory,
            DynamoDbRelationReviewPublishRequestBuilder requestBuilder,
            DynamoDbRelationReviewPublishExecutor executor, Clock clock) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        this.aead = Objects.requireNonNull(aead, "aead must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.targetSetDigester = Objects.requireNonNull(targetSetDigester, "targetSetDigester must not be null");
        this.keyReferences = Objects.requireNonNull(keyReferences, "keyReferences must not be null");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore must not be null");
        this.actionFactory = Objects.requireNonNull(actionFactory, "actionFactory must not be null");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PublicationResult publish(RelationReviewDraft draft, MemoryIdempotencyBinding processingBinding) {
        Objects.requireNonNull(draft, "draft must not be null");
        requireProcessingBinding(processingBinding);
        String reviewId = UUID.randomUUID().toString();
        long reviewVersion = 1;
        keyProvider.probeRelationPurpose();
        var activeKey = keyProvider.activeEncryptionKey();
        if (targetSetDigester instanceof HmacRelationTargetSetDigester hmacDigester)
            hmacDigester.assertSeparateFrom(activeKey.key());
        int keyVersion = activeKey.version();
        String targetDigest = targetSetDigester.digestDraft(draft);
        OffsetDateTime createdAt = now();
        OffsetDateTime expiresAt = createdAt.plusMinutes(30);

        if (draft.targets().size() <= DIRECT_TARGET_LIMIT) {
            EncryptedPayloads encrypted = encrypt(draft, reviewId, reviewVersion, keyVersion);
            List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = actionFactory.directActions(
                    draft, reviewId, reviewVersion, keyVersion, targetDigest, expiresAt,
                    encrypted.candidate(), encrypted.targets(), processingBinding);
            requestBuilder.buildDirect(draft, actions);
            TransactWriteItemsResponse response = executor.publishDirect(draft, actions);
            if (response == null) throw new RelationControlUnavailableException();
            return new PublicationResult(reviewId, reviewVersion, keyVersion, draft.targets().size(), false, true);
        }

        String stageOwner = UUID.randomUUID().toString();
        RelationKeyReference.Key referenceKey = new RelationKeyReference.Key(keyVersion, reviewId);
        RelationKeyReference reference = new RelationKeyReference(referenceKey, RelationKeyReference.State.PREPARING,
                reviewVersion, createdAt);
        RelationKeyReferenceStore.CreateResult created = keyReferences.createPreparingIfAbsent(reference);
        if (created != RelationKeyReferenceStore.CreateResult.CREATED)
            throw new RelationControlUnavailableException();

        boolean executorInvoked = false;
        try {
            stageStore.createPreparingMetadata(reviewId, reviewVersion, keyVersion, draft, targetDigest,
                    stageOwner, createdAt, expiresAt);
            EncryptedPayloads encrypted = encrypt(draft, reviewId, reviewVersion, keyVersion);
            stageStore.stageAndVerify(reviewId, reviewVersion, keyVersion, stageOwner,
                    encrypted.candidate(), encrypted.targets(), targetDigest);
            List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = actionFactory.stagedActions(
                    draft, reviewId, reviewVersion, keyVersion, targetDigest, stageOwner, expiresAt, processingBinding);
            // Validate the full transaction before entering the executor. A validation failure is
            // definitely pre-send and can be cleaned; an executor failure may be outcome-unknown.
            requestBuilder.buildStaged(draft, actions, draft.targets().size(), targetDigest);
            // Do not clean staged rows after this call throws: a timeout can mean the atomic
            // publish transaction committed. Reconciliation must resolve the outcome first.
            executorInvoked = true;
            TransactWriteItemsResponse response = executor.publishStaged(draft, actions,
                    draft.targets().size(), targetDigest);
            if (response == null) throw new RelationControlUnavailableException();
            return new PublicationResult(reviewId, reviewVersion, keyVersion, draft.targets().size(), true, true);
        } catch (RuntimeException failure) {
            if (!executorInvoked) cleanupFailedStage(reviewId, stageOwner, referenceKey, reviewVersion);
            throw failure;
        }
    }

    private void cleanupFailedStage(String reviewId, String stageOwner, RelationKeyReference.Key referenceKey,
            long reviewVersion) {
        try { stageStore.cleanupOwnedCiphertext(reviewId, stageOwner); }
        catch (RuntimeException ignored) { /* Cleanup remains pending; never release the reference here. */ }
        try { stageStore.markCleanupPending(reviewId, stageOwner, now()); }
        catch (RuntimeException ignored) { /* A sweeper must reconcile a missing or unreadable META item. */ }
        try {
            keyReferences.transition(referenceKey, reviewVersion, RelationKeyReference.State.PREPARING,
                    RelationKeyReference.State.CLEANUP_PENDING);
        } catch (RuntimeException ignored) { /* Fail closed; the PREPARING reference remains a recovery signal. */ }
    }

    private EncryptedPayloads encrypt(RelationReviewDraft draft, String reviewId, long reviewVersion, int keyVersion) {
        byte[] candidatePlaintext = codec.encodeCandidate(draft);
        RelationCiphertext candidate;
        try {
            candidate = aead.encrypt(candidatePlaintext, new RelationStateBinding(reviewId, reviewVersion,
                    RelationStateBinding.SourceOperation.REGISTER, RelationStateBinding.PayloadKind.CANDIDATE, -1));
        } finally { Arrays.fill(candidatePlaintext, (byte) 0); }
        if (candidate.keyVersion() != keyVersion) throw new RelationCryptoException();
        List<RelationReviewStageStore.EncryptedTarget> targets = new ArrayList<>(draft.targets().size());
        for (int ordinal = 0; ordinal < draft.targets().size(); ordinal++) {
            RelationReviewDraft.Target target = draft.targets().get(ordinal);
            byte[] plaintext = codec.encodeTargetPreview(draft, target);
            RelationCiphertext encrypted;
            try {
                encrypted = aead.encrypt(plaintext, new RelationStateBinding(reviewId, reviewVersion,
                        RelationStateBinding.SourceOperation.REGISTER, RelationStateBinding.PayloadKind.TARGET_PREVIEW,
                        ordinal));
            } finally { Arrays.fill(plaintext, (byte) 0); }
            if (encrypted.keyVersion() != keyVersion) throw new RelationCryptoException();
            targets.add(new RelationReviewStageStore.EncryptedTarget(ordinal,
                    target.snapshot().memoryId().value(), target.preview().expectedTargetVersion(),
                    target.preview().operation().name(), encrypted));
        }
        return new EncryptedPayloads(candidate, List.copyOf(targets));
    }

    private static void requireProcessingBinding(MemoryIdempotencyBinding binding) {
        Objects.requireNonNull(binding, "processingBinding must not be null");
        if (binding.status() != MemoryIdempotencyBinding.Status.PROCESSING
                || !"MEMORY_REGISTER".equals(binding.identity().operationType()))
            throw new IllegalArgumentException("a PROCESSING MEMORY_REGISTER Binding is required");
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(PROJECT_TIMEZONE); }

    private record EncryptedPayloads(RelationCiphertext candidate,
            List<RelationReviewStageStore.EncryptedTarget> targets) { }

    public record PublicationResult(String reviewId, long reviewVersion, int keyVersion, int targetCount,
            boolean staged, boolean transactionSucceeded) {
        public PublicationResult {
            Objects.requireNonNull(reviewId, "reviewId must not be null");
            if (reviewVersion < 1 || keyVersion < 1 || targetCount < 1 || targetCount > 100)
                throw new IllegalArgumentException("invalid publication result");
        }
    }
}
