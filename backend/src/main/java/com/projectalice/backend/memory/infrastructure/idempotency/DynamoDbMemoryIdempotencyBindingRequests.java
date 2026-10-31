package com.projectalice.backend.memory.infrastructure.idempotency;

import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Pure conditional DynamoDB actions for Binding reservation and terminal publication. */
public final class DynamoDbMemoryIdempotencyBindingRequests {
    private DynamoDbMemoryIdempotencyBindingRequests() { }

    public static TransactWriteItem reservationPut(String tableName, MemoryIdempotencyBinding binding) {
        requireTable(tableName);
        Objects.requireNonNull(binding, "binding must not be null");
        if (binding.status() != MemoryIdempotencyBinding.Status.PROCESSING)
            throw new IllegalArgumentException("only PROCESSING Bindings can be reserved");
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName)
                .item(DynamoDbMemoryIdempotencyBindingMapper.toItem(binding))
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    /**
     * Returns an action to be combined with the Review Publish transaction. It must not be sent
     * independently: Review publication and Binding completion share this same conditional write.
     */
    public static TransactWriteItem completeForReview(String tableName, MemoryIdempotencyBinding processing,
            String reviewId, long reviewVersion, String safeOutcomeCode, java.time.OffsetDateTime completedAt) {
        requireTable(tableName);
        Objects.requireNonNull(processing, "processing must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (processing.status() != MemoryIdempotencyBinding.Status.PROCESSING)
            throw new IllegalArgumentException("only PROCESSING Bindings can complete");
        if (reviewVersion < 1) throw new IllegalArgumentException("reviewVersion must be positive");
        MemoryIdempotencyBinding.ResultReference result = new MemoryIdempotencyBinding.ResultReference(
                MemoryIdempotencyBinding.ResultType.REVIEW, reviewId, reviewVersion);
        new MemoryIdempotencyBinding(processing.identity(), processing.requestDigest(), processing.operationId(),
                MemoryIdempotencyBinding.Status.COMPLETED, processing.leaseEpoch(), processing.leaseOwner(),
                processing.leaseExpiresAt(), result, safeOutcomeCode, processing.createdAt(), completedAt, null);
        Map<String, String> names = Map.of("#status", "status", "#operationType", "operationType", "#requestDigest", "requestDigest",
                "#leaseEpoch", "leaseEpoch", "#leaseOwner", "leaseOwner", "#resultType", "resultType",
                "#resultReference", "resultReference", "#safeOutcomeCode", "safeOutcomeCode",
                "#updatedAt", "updatedAt");
        Map<String, AttributeValue> values = Map.of(":processing", s("PROCESSING"),
                ":operationType", s(processing.identity().operationType()),
                ":requestDigest", s(processing.requestDigest()), ":leaseEpoch", n(processing.leaseEpoch()),
                ":leaseOwner", s(processing.leaseOwner()), ":completed", s("COMPLETED"),
                ":reviewType", s("REVIEW"), ":resultReference", DynamoDbMemoryIdempotencyBindingMapper.resultReference(result),
                ":outcome", s(safeOutcomeCode), ":updatedAt", s(completedAt.toString()));
        return TransactWriteItem.builder().update(Update.builder().tableName(tableName)
                .key(Map.of("pk", s(processing.identity().partitionKey()), "sk", s("META")))
                .updateExpression("SET #status = :completed, #resultType = :reviewType, "
                        + "#resultReference = :resultReference, #safeOutcomeCode = :outcome, #updatedAt = :updatedAt")
                .conditionExpression("#status = :processing AND #operationType = :operationType AND #requestDigest = :requestDigest "
                        + "AND #leaseOwner = :leaseOwner AND #leaseEpoch = :leaseEpoch")
                .expressionAttributeNames(names).expressionAttributeValues(values).build()).build();
    }

    private static void requireTable(String tableName) {
        Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
