package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
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
}
