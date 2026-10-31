package com.projectalice.backend.memory.infrastructure.relation;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

/** Conditional persistence for PREPARING references; publication/release require Review transactions. */
public final class DynamoDbRelationKeyReferenceStore implements RelationKeyReferenceStore {
    private final DynamoDbClient client;
    private final String tableName;
    private final Clock clock;

    public DynamoDbRelationKeyReferenceStore(DynamoDbClient client, String tableName, Clock clock) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    @Override public CreateResult createPreparingIfAbsent(RelationKeyReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        if (reference.state() != RelationKeyReference.State.PREPARING)
            throw new IllegalArgumentException("new references must start PREPARING");
        int version = reference.identity().keyVersion();
        String versionName = Integer.toString(version);
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                            .key(Map.of("pk", s(DynamoDbRelationControlRepository.KEY_PK),
                                    "sk", s(DynamoDbRelationControlRepository.CONTROL_SK)))
                            .updateExpression("SET #versions.#v.#preparing = #versions.#v.#preparing + :one, "
                                    + "#controlVersion = #controlVersion + :one")
                            .conditionExpression("#itemType = :itemType AND #versions.#v.#state = :writeActive "
                                    + "AND attribute_exists(#controlVersion) AND #versions.#v.#preparing >= :zero")
                            .expressionAttributeNames(Map.of("#itemType", "itemType", "#versions", "versions",
                                    "#v", versionName, "#preparing", "preparingReferenceCount",
                                    "#state", "state", "#controlVersion", "controlVersion"))
                            .expressionAttributeValues(Map.of(":itemType", s("MEMORY_RELATION_KEY_CONTROL"),
                                    ":writeActive", s("WRITE_ACTIVE"), ":one", n(1), ":zero", n(0))).build()).build(),
                    TransactWriteItem.builder().put(Put.builder().tableName(tableName)
                            .item(referenceItem(reference))
                            .conditionExpression("attribute_not_exists(pk)").build()).build()).build());
            return CreateResult.CREATED;
        } catch (TransactionCanceledException conflict) {
            Map<String, AttributeValue> existing = readReference(reference.identity());
            if (existing.isEmpty()) throw new RelationControlUnavailableException();
            if (sameReference(existing, reference)) return CreateResult.SAME_REFERENCE_EXISTS;
            return CreateResult.IDENTITY_CONFLICT;
        } catch (RuntimeException unavailable) {
            throw new RelationControlUnavailableException();
        }
    }

    @Override public TransitionResult transition(RelationKeyReference.Key identity, long expectedReviewVersion,
            RelationKeyReference.State expectedState, RelationKeyReference.State targetState) {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(expectedState, "expectedState must not be null");
        Objects.requireNonNull(targetState, "targetState must not be null");
        if (expectedReviewVersion < 1) throw new IllegalArgumentException("expectedReviewVersion must be positive");
        // Publishing or cleaning an ACTIVE Review must include its Review Metadata and Active Directory
        // in the same transaction. Those lifecycle resources are intentionally outside this step.
        if (targetState == RelationKeyReference.State.ACTIVE
                || expectedState == RelationKeyReference.State.ACTIVE
                || targetState == RelationKeyReference.State.RELEASED)
            return TransitionResult.REVIEW_LIFECYCLE_TRANSACTION_REQUIRED;
        if (expectedState != RelationKeyReference.State.PREPARING
                || targetState != RelationKeyReference.State.CLEANUP_PENDING)
            throw new IllegalArgumentException("unsupported reference state transition");
        String version = Integer.toString(identity.keyVersion());
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                            .key(Map.of("pk", s(DynamoDbRelationControlRepository.KEY_PK),
                                    "sk", s(DynamoDbRelationControlRepository.CONTROL_SK)))
                            .updateExpression("SET #versions.#v.#preparing = #versions.#v.#preparing - :one, "
                                    + "#versions.#v.#cleanup = #versions.#v.#cleanup + :one, "
                                    + "#controlVersion = #controlVersion + :one")
                            .conditionExpression("#itemType = :itemType AND #versions.#v.#preparing > :zero "
                                    + "AND attribute_exists(#controlVersion)")
                            .expressionAttributeNames(Map.of("#itemType", "itemType", "#versions", "versions",
                                    "#v", version, "#preparing", "preparingReferenceCount",
                                    "#cleanup", "cleanupPendingCount", "#controlVersion", "controlVersion"))
                            .expressionAttributeValues(Map.of(":itemType", s("MEMORY_RELATION_KEY_CONTROL"),
                                    ":one", n(1), ":zero", n(0))).build()).build(),
                    TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                            .key(referenceKey(identity))
                            .updateExpression("SET #state = :target, #updatedAt = :updatedAt")
                            .conditionExpression("#state = :expected AND #reviewVersion = :reviewVersion")
                            .expressionAttributeNames(Map.of("#state", "referenceState",
                                    "#reviewVersion", "reviewVersion", "#updatedAt", "updatedAt"))
                            .expressionAttributeValues(Map.of(":target", s("CLEANUP_PENDING"),
                                    ":expected", s("PREPARING"), ":reviewVersion", n(expectedReviewVersion),
                                    ":updatedAt", s(now()))).build()).build()).build());
            return TransitionResult.UPDATED;
        } catch (TransactionCanceledException conflict) {
            Map<String, AttributeValue> existing = readReference(identity);
            if (existing.isEmpty()) return TransitionResult.NOT_FOUND;
            if ("CLEANUP_PENDING".equals(str(existing, "referenceState"))
                    && number(existing, "reviewVersion") == expectedReviewVersion)
                return TransitionResult.ALREADY_AT_TARGET;
            return TransitionResult.VERSION_OR_STATE_CONFLICT;
        } catch (RuntimeException unavailable) {
            throw new RelationControlUnavailableException();
        }
    }

    @Override public ReleaseResult releaseAfterCleanup(RelationKeyReference.Key identity, long expectedReviewVersion) {
        Objects.requireNonNull(identity, "identity must not be null");
        if (expectedReviewVersion < 1) throw new IllegalArgumentException("expectedReviewVersion must be positive");
        // Exact release also requires Review.activeSlotReleased and Content-cleanup evidence atomically.
        return ReleaseResult.REVIEW_LIFECYCLE_TRANSACTION_REQUIRED;
    }

    private Map<String, AttributeValue> readReference(RelationKeyReference.Key identity) {
        var response = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(referenceKey(identity)).consistentRead(true).build());
        return response.item() == null ? Map.of() : response.item();
    }

    private static Map<String, AttributeValue> referenceKey(RelationKeyReference.Key identity) {
        return Map.of("pk", s(DynamoDbRelationControlRepository.KEY_PK + "#" + identity.keyVersion()),
                "sk", s("REVIEW#" + identity.reviewId()));
    }

    private static Map<String, AttributeValue> referenceItem(RelationKeyReference ref) {
        Map<String, AttributeValue> item = new java.util.HashMap<>(referenceKey(ref.identity()));
        item.put("itemType", s("MEMORY_RELATION_KEY_REFERENCE"));
        item.put("schemaVersion", n(1));
        item.put("relationReviewId", s(ref.identity().reviewId()));
        item.put("keyVersion", n(ref.identity().keyVersion()));
        item.put("referenceState", s("PREPARING"));
        item.put("reviewVersion", n(ref.reviewVersion()));
        item.put("createdAt", s(ref.createdAt().toString()));
        item.put("updatedAt", s(ref.createdAt().toString()));
        return Map.copyOf(item);
    }

    private static boolean sameReference(Map<String, AttributeValue> item, RelationKeyReference expected) {
        return "MEMORY_RELATION_KEY_REFERENCE".equals(str(item, "itemType"))
                && expected.identity().reviewId().equals(str(item, "relationReviewId"))
                && expected.identity().keyVersion() == number(item, "keyVersion")
                && expected.reviewVersion() == number(item, "reviewVersion")
                && "PREPARING".equals(str(item, "referenceState"));
    }

    private String now() { return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(9)).toString(); }
    private static String str(Map<String, AttributeValue> item, String name) { return item.get(name).s(); }
    private static long number(Map<String, AttributeValue> item, String name) { return Long.parseLong(item.get(name).n()); }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
