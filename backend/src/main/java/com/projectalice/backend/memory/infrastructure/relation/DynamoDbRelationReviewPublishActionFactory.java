package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.infrastructure.idempotency.DynamoDbMemoryIdempotencyBindingRequests;
import com.projectalice.backend.memory.infrastructure.idempotency.MemoryIdempotencyBinding;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

/** Builds the non-ciphertext actions for the atomic Relation Review publication transaction. */
public final class DynamoDbRelationReviewPublishActionFactory {
    private static final String GLOBAL_PK = DynamoDbRelationControlRepository.GLOBAL_PK;
    private static final String KEY_PK = DynamoDbRelationControlRepository.KEY_PK;
    private final String tableName;
    private final DynamoDbRelationControlRepository controls;
    private final DynamoDbRelationReviewStageStore stageStore;
    private final Clock clock;

    public DynamoDbRelationReviewPublishActionFactory(String tableName, DynamoDbRelationControlRepository controls,
            DynamoDbRelationReviewStageStore stageStore, Clock clock) {
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.controls = Objects.requireNonNull(controls, "controls must not be null");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    public List<DynamoDbRelationReviewPublishRequestBuilder.Action> directActions(RelationReviewDraft draft,
            String reviewId, long reviewVersion, int keyVersion, String targetSetDigest,
            OffsetDateTime expiresAt, RelationCiphertext candidate, List<RelationReviewStageStore.EncryptedTarget> targets,
            MemoryIdempotencyBinding processingBinding) {
        requireProcessingBinding(processingBinding);
        var snapshot = controls.readControls();
        var global = snapshot.global();
        var key = snapshot.keys();
        if (key.writeKeyVersion() != keyVersion) throw new RelationControlUnavailableException();
        var keyVersionState = key.versions().stream().filter(v -> v.version() == keyVersion).findFirst()
                .orElseThrow(RelationControlUnavailableException::new);
        if (keyVersionState.state() != RelationKeyControl.KeyState.WRITE_ACTIVE)
            throw new RelationControlUnavailableException();
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = new ArrayList<>();
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.GLOBAL_CONTROL,
                globalControlAction(global.activeCount(), global.controlVersion())));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_CONTROL,
                keyControlAction(keyVersion, key.controlVersion(), false)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_REFERENCE,
                putActiveReference(reviewId, reviewVersion, keyVersion)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.REVIEW_METADATA,
                putActiveReview(draft, reviewId, reviewVersion, keyVersion, targetSetDigest, targets.size(), null, expiresAt)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.CANDIDATE_CIPHERTEXT,
                putCipher(stageStore.candidateItem(reviewId, reviewVersion, keyVersion, null, candidate))));
        for (RelationReviewStageStore.EncryptedTarget target : targets) {
            actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT,
                    putCipher(stageStore.targetItem(reviewId, reviewVersion, keyVersion, null, target))));
        }
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.ACTIVE_DIRECTORY,
                putDirectory(reviewId, reviewVersion, keyVersion, global.controlVersion() + 1, expiresAt)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.IDEMPOTENCY_BINDING,
                DynamoDbMemoryIdempotencyBindingRequests.completeForReview(tableName, processingBinding,
                        reviewId, reviewVersion, "REVIEW_REQUIRED", now())));
        return List.copyOf(actions);
    }

    public List<DynamoDbRelationReviewPublishRequestBuilder.Action> stagedActions(RelationReviewDraft draft,
            String reviewId, long reviewVersion, int keyVersion, String targetSetDigest, String stageOwner,
            OffsetDateTime expiresAt, MemoryIdempotencyBinding processingBinding) {
        requireProcessingBinding(processingBinding);
        var snapshot = controls.readControls();
        var global = snapshot.global();
        var key = snapshot.keys();
        if (key.writeKeyVersion() != keyVersion) throw new RelationControlUnavailableException();
        var keyVersionState = key.versions().stream().filter(v -> v.version() == keyVersion).findFirst()
                .orElseThrow(RelationControlUnavailableException::new);
        if (keyVersionState.state() != RelationKeyControl.KeyState.WRITE_ACTIVE
                || keyVersionState.preparingReferenceCount() < 1)
            throw new RelationControlUnavailableException();
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = new ArrayList<>();
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.GLOBAL_CONTROL,
                globalControlAction(global.activeCount(), global.controlVersion())));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_CONTROL,
                keyControlAction(keyVersion, key.controlVersion(), true)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_REFERENCE,
                activateReference(reviewId, reviewVersion, keyVersion)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.REVIEW_METADATA,
                activateReview(reviewId, reviewVersion, targetSetDigest, draft.targets().size(), stageOwner)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.ACTIVE_DIRECTORY,
                putDirectory(reviewId, reviewVersion, keyVersion, global.controlVersion() + 1, expiresAt)));
        actions.add(action(DynamoDbRelationReviewPublishRequestBuilder.Role.IDEMPOTENCY_BINDING,
                DynamoDbMemoryIdempotencyBindingRequests.completeForReview(tableName, processingBinding,
                        reviewId, reviewVersion, "REVIEW_REQUIRED", now())));
        return List.copyOf(actions);
    }

    private TransactWriteItem globalControlAction(int activeCount, long controlVersion) {
        return TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                .key(Map.of("pk", s(GLOBAL_PK), "sk", s(DynamoDbRelationControlRepository.CONTROL_SK)))
                .updateExpression("SET #activeCount = #activeCount + :one, #controlVersion = #controlVersion + :one")
                .conditionExpression("#itemType = :itemType AND #activeCount < :maxActive AND #maxActive = :maxActive "
                        + "AND #controlVersion = :expectedVersion")
                .expressionAttributeNames(Map.of("#itemType", "itemType", "#activeCount", "activeCount",
                        "#maxActive", "maxActiveCount", "#controlVersion", "controlVersion"))
                .expressionAttributeValues(Map.of(":itemType", s("MEMORY_RELATION_REVIEW_CONTROL"),
                        ":one", n(1), ":maxActive", n(20), ":expectedVersion", n(controlVersion))).build()).build();
    }

    private TransactWriteItem keyControlAction(int version, long controlVersion, boolean staged) {
        String v = Integer.toString(version);
        String update = "SET #versions.#v.activeReviewCount = #versions.#v.activeReviewCount + :one, "
                + "#controlVersion = #controlVersion + :one";
        String condition = "#itemType = :itemType AND #writeVersion = :version AND #versions.#v.#state = :writeActive "
                + "AND #controlVersion = :expectedVersion AND #versions.#v.activeReviewCount >= :zero";
        if (staged) {
            update = "SET #versions.#v.activeReviewCount = #versions.#v.activeReviewCount + :one, "
                    + "#versions.#v.preparingReferenceCount = #versions.#v.preparingReferenceCount - :one, "
                    + "#controlVersion = #controlVersion + :one";
            condition += " AND #versions.#v.preparingReferenceCount > :zero";
        }
        Map<String, String> names = new HashMap<>(Map.of("#itemType", "itemType", "#writeVersion", "writeKeyVersion",
                "#versions", "versions", "#v", v, "#state", "state", "#controlVersion", "controlVersion"));
        return TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                .key(Map.of("pk", s(KEY_PK), "sk", s(DynamoDbRelationControlRepository.CONTROL_SK)))
                .updateExpression(update).conditionExpression(condition).expressionAttributeNames(names)
                .expressionAttributeValues(Map.of(":itemType", s("MEMORY_RELATION_KEY_CONTROL"),
                        ":version", n(version), ":writeActive", s("WRITE_ACTIVE"), ":expectedVersion", n(controlVersion),
                        ":one", n(1), ":zero", n(0))).build()).build();
    }

    private TransactWriteItem putActiveReference(String reviewId, long reviewVersion, int keyVersion) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", s(KEY_PK + "#" + keyVersion)); item.put("sk", s("REVIEW#" + reviewId));
        item.put("itemType", s("MEMORY_RELATION_KEY_REFERENCE")); item.put("schemaVersion", n(1));
        item.put("relationReviewId", s(reviewId)); item.put("keyVersion", n(keyVersion));
        item.put("referenceState", s("ACTIVE")); item.put("reviewVersion", n(reviewVersion));
        item.put("createdAt", s(now().toString())); item.put("updatedAt", s(now().toString()));
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(Map.copyOf(item))
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    private TransactWriteItem activateReference(String reviewId, long reviewVersion, int keyVersion) {
        return TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                .key(Map.of("pk", s(KEY_PK + "#" + keyVersion), "sk", s("REVIEW#" + reviewId)))
                .updateExpression("SET #state = :active, #updatedAt = :updatedAt")
                .conditionExpression("#state = :preparing AND #reviewVersion = :reviewVersion")
                .expressionAttributeNames(Map.of("#state", "referenceState", "#reviewVersion", "reviewVersion", "#updatedAt", "updatedAt"))
                .expressionAttributeValues(Map.of(":active", s("ACTIVE"), ":preparing", s("PREPARING"),
                        ":reviewVersion", n(reviewVersion), ":updatedAt", s(now().toString()))).build()).build();
    }

    private TransactWriteItem putActiveReview(RelationReviewDraft draft, String reviewId, long reviewVersion,
            int keyVersion, String digest, int targetCount, String stageOwner, OffsetDateTime expiresAt) {
        Map<String, AttributeValue> item = reviewMetadata(draft, reviewId, reviewVersion, keyVersion, digest, targetCount, stageOwner, expiresAt);
        item.put("status", s("ACTIVE"));
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(Map.copyOf(item))
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    private TransactWriteItem activateReview(String reviewId, long reviewVersion, String digest,
            int targetCount, String stageOwner) {
        return TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                .key(Map.of("pk", s(reviewPk(reviewId)), "sk", s("META")))
                .updateExpression("SET #status = :active, #updatedAt = :updatedAt, #publishedAt = :updatedAt")
                .conditionExpression("#status = :preparing AND #reviewVersion = :reviewVersion "
                        + "AND #targetCount = :targetCount AND #targetSetDigest = :targetSetDigest AND #stageOwner = :stageOwner")
                .expressionAttributeNames(Map.of("#status", "status", "#reviewVersion", "reviewVersion",
                        "#targetCount", "targetCount", "#targetSetDigest", "targetSetDigest", "#stageOwner", "stageOwner",
                        "#updatedAt", "updatedAt", "#publishedAt", "publishedAt"))
                .expressionAttributeValues(Map.of(":active", s("ACTIVE"), ":preparing", s("PREPARING"),
                        ":reviewVersion", n(reviewVersion), ":targetCount", n(targetCount),
                        ":targetSetDigest", s(digest), ":stageOwner", s(stageOwner), ":updatedAt", s(now().toString()))).build()).build();
    }

    private Map<String, AttributeValue> reviewMetadata(RelationReviewDraft draft, String reviewId, long reviewVersion,
            int keyVersion, String digest, int targetCount, String stageOwner, OffsetDateTime expires) {
        OffsetDateTime created = now();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", s(reviewPk(reviewId))); item.put("sk", s("META"));
        item.put("itemType", s("MEMORY_RELATION_REVIEW")); item.put("schemaVersion", n(1));
        item.put("relationReviewId", s(reviewId)); item.put("reviewVersion", n(reviewVersion));
        item.put("sourceOperation", s("REGISTER")); item.put("relationType", s(draft.relationType().name()));
        item.put("stateKeyVersion", n(keyVersion)); item.put("targetCount", n(targetCount));
        item.put("targetSetDigest", s(digest)); item.put("candidateCiphertextSk", s("CANDIDATE"));
        item.put("createdAt", s(created.toString())); item.put("updatedAt", s(created.toString()));
        item.put("reviewExpiresAt", s(expires.toString())); item.put("expiresAtEpochSeconds", n(expires.toEpochSecond()));
        item.put("cleanupState", s("NOT_STARTED")); item.put("cleanupEpoch", n(0));
        item.put("activeSlotReleased", bool(false));
        if (stageOwner != null) item.put("stageOwner", s(stageOwner));
        return item;
    }

    private TransactWriteItem putDirectory(String reviewId, long reviewVersion, int keyVersion, long controlVersion, OffsetDateTime expires) {
        Map<String, AttributeValue> item = Map.ofEntries(
                Map.entry("pk", s("MEMORY_RELATION_REVIEW#ACTIVE")), Map.entry("sk", s("REVIEW#" + reviewId)),
                Map.entry("itemType", s("MEMORY_RELATION_REVIEW_ACTIVE_DIRECTORY")), Map.entry("schemaVersion", n(1)),
                Map.entry("relationReviewId", s(reviewId)), Map.entry("reviewVersion", n(reviewVersion)),
                Map.entry("status", s("ACTIVE")), Map.entry("cleanupState", s("NOT_STARTED")),
                Map.entry("reviewExpiresAt", s(expires.toString())), Map.entry("expiresAtEpochSeconds", n(expires.toEpochSecond())),
                Map.entry("stateKeyVersion", n(keyVersion)), Map.entry("controlVersion", n(controlVersion)));
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(item)
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    private TransactWriteItem putCipher(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName).item(item)
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    private static DynamoDbRelationReviewPublishRequestBuilder.Action action(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, TransactWriteItem item) {
        return new DynamoDbRelationReviewPublishRequestBuilder.Action(role, item);
    }
    private static void requireProcessingBinding(MemoryIdempotencyBinding binding) {
        Objects.requireNonNull(binding, "processingBinding must not be null");
        if (binding.status() != MemoryIdempotencyBinding.Status.PROCESSING)
            throw new IllegalArgumentException("Binding must be PROCESSING before publication");
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(9)); }
    private static String reviewPk(String id) { return "MEMORY_RELATION_REVIEW#" + java.util.UUID.fromString(id); }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static AttributeValue bool(boolean value) { return AttributeValue.builder().bool(value).build(); }
}
