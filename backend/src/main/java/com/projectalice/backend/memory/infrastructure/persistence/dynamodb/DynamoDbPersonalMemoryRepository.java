package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.application.port.out.MemoryUpdateConflictException;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

public final class DynamoDbPersonalMemoryRepository implements PersonalMemoryRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbPersonalMemoryRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = Objects.requireNonNull(dynamoDbClient, "dynamoDbClient must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
    }

    @Override
    public void save(PersonalMemory memory) {
        Objects.requireNonNull(memory, "memory must not be null");

        Put memoryPut = Put.builder()
                .tableName(tableName)
                .item(DynamoDbPersonalMemoryMapper.toMemoryItem(memory))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)")
                .build();

        Put revisionPut = Put.builder()
                .tableName(tableName)
                .item(DynamoDbPersonalMemoryMapper.toCreateRevisionItem(memory))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)")
                .build();

        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(List.of(
                        TransactWriteItem.builder().put(memoryPut).build(),
                        TransactWriteItem.builder().put(revisionPut).build()))
                .build());
    }

    @Override
    public Optional<PersonalMemory> findById(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");

        Map<String, AttributeValue> key = Map.of(
                "pk", AttributeValue.builder()
                        .s("MEMORY#" + memoryId.value())
                        .build(),
                "sk", AttributeValue.builder()
                        .s("META")
                        .build());
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DynamoDbPersonalMemoryMapper.toDomain(response.item()));
    }

    @Override
    public void update(PersonalMemory memory, long expectedVersion) {
        Objects.requireNonNull(memory, "memory must not be null");
        if (memory.version() != expectedVersion + 1) {
            throw new IllegalArgumentException(
                    "memory.version() must equal expectedVersion + 1, but was "
                            + memory.version() + " for expectedVersion " + expectedVersion);
        }

        Map<String, AttributeValue> key = memoryKey(memory.memoryId());

        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .consistentRead(true)
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            throw new MemoryUpdateConflictException(
                    "PersonalMemory not found for update: " + memory.memoryId().value());
        }

        PersonalMemory current = DynamoDbPersonalMemoryMapper.toDomain(response.item());
        if (current.version() != expectedVersion) {
            throw new MemoryUpdateConflictException(
                    "Expected version " + expectedVersion + " but current version is "
                            + current.version() + " for memory " + memory.memoryId().value());
        }

        long nextVersion = expectedVersion + 1;
        List<String> changedFields = changedFields(current, memory);

        // Management API Semantic Update is always an explicit edit: confirmedAt = changedAt.
        OffsetDateTime changedAt = memory.updatedAt();
        PersonalMemory afterPersisted = PersonalMemory.reconstitute(
                memory.memoryId(),
                memory.content(),
                memory.category(),
                memory.captureType(),
                memory.sensitivityLevel(),
                memory.state(),
                memory.version(),
                memory.createdAt(),
                changedAt,
                changedAt);

        Update memoryUpdate = Update.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpression())
                .conditionExpression(
                        "#itemType = :personalMemoryType AND #version = :expected AND #version < :longMax")
                .expressionAttributeNames(Map.of("#itemType", "itemType", "#version", "version"))
                .expressionAttributeValues(
                        updateExpressionValues(afterPersisted, expectedVersion, nextVersion))
                .build();

        Put revisionPut = Put.builder()
                .tableName(tableName)
                .item(DynamoDbPersonalMemoryMapper.toUpdateRevisionItem(
                        current, afterPersisted, expectedVersion, nextVersion, changedFields,
                        reasonCode(changedFields)))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)")
                .build();

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(List.of(
                            TransactWriteItem.builder().update(memoryUpdate).build(),
                            TransactWriteItem.builder().put(revisionPut).build()))
                    .build());
        } catch (TransactionCanceledException exception) {
            throw new MemoryUpdateConflictException(
                    "Semantic update transaction was cancelled for memory "
                            + memory.memoryId().value(),
                    exception);
        }
    }

    private static Map<String, AttributeValue> memoryKey(MemoryId memoryId) {
        return Map.of(
                "pk", AttributeValue.builder().s("MEMORY#" + memoryId.value()).build(),
                "sk", AttributeValue.builder().s("META").build());
    }

    private static List<String> changedFields(PersonalMemory before, PersonalMemory after) {
        List<String> changed = new ArrayList<>();
        if (!before.content().equals(after.content())) {
            changed.add("CONTENT");
        }
        if (before.category() != after.category()) {
            changed.add("CATEGORY");
        }
        if (before.sensitivityLevel() != after.sensitivityLevel()) {
            changed.add("SENSITIVITY_LEVEL");
        }
        if (before.state() != after.state()) {
            changed.add("MEMORY_STATE");
        }
        return changed;
    }

    private static String reasonCode(List<String> changedFields) {
        if (changedFields.contains("CONTENT")) {
            return "CONTENT_EDIT";
        }
        if (changedFields.contains("CATEGORY") || changedFields.contains("SENSITIVITY_LEVEL")) {
            return "CLASSIFICATION_CORRECTION";
        }
        if (changedFields.contains("MEMORY_STATE")) {
            return "STATE_TRANSITION";
        }
        return "CONTENT_EDIT";
    }

    private static String updateExpression() {
        return "SET content = :content, category = :category, sensitivityLevel = :sensitivityLevel, "
                + "memoryState = :memoryState, #version = :nextVersion, updatedAt = :updatedAt, "
                + "confirmedAt = :confirmedAt";
    }

    private static Map<String, AttributeValue> updateExpressionValues(
            PersonalMemory afterPersisted, long expectedVersion, long nextVersion) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":content", AttributeValue.builder().s(afterPersisted.content().value()).build());
        values.put(":category", AttributeValue.builder().s(afterPersisted.category().name()).build());
        values.put(":sensitivityLevel",
                AttributeValue.builder().s(afterPersisted.sensitivityLevel().name()).build());
        values.put(":memoryState", AttributeValue.builder().s(afterPersisted.state().name()).build());
        values.put(":nextVersion", AttributeValue.builder().n(Long.toString(nextVersion)).build());
        values.put(":updatedAt",
                AttributeValue.builder().s(afterPersisted.updatedAt().toString()).build());
        values.put(":confirmedAt",
                AttributeValue.builder().s(afterPersisted.confirmedAt().toString()).build());
        values.put(":personalMemoryType", AttributeValue.builder().s("PERSONAL_MEMORY").build());
        values.put(":expected", AttributeValue.builder().n(Long.toString(expectedVersion)).build());
        values.put(":longMax", AttributeValue.builder().n(Long.toString(Long.MAX_VALUE)).build());
        return values;
    }
}
