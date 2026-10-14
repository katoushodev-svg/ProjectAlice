package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/** DynamoDB persistence boundary for staged, content-free term pages. */
public final class DynamoDbMemorySearchTermPageStore {
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbMemorySearchTermPageStore(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    public void put(KwV1MemoryProjectionBuilder.Projection projection, int pageNumber) {
        client.putItem(PutItemRequest.builder().tableName(tableName)
                .item(DynamoDbMemorySearchTermPageMapper.map(projection, pageNumber)).build());
    }

    /** Reuses a deterministic staged page only after checking its persisted content. */
    public void putIfAbsentOrSame(KwV1MemoryProjectionBuilder.Projection projection, int pageNumber) {
        Map<String, AttributeValue> expected = DynamoDbMemorySearchTermPageMapper.map(projection, pageNumber);
        Map<String, AttributeValue> key = Map.of("pk", expected.get("pk"), "sk", expected.get("sk"));
        Map<String, AttributeValue> existing = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(key).consistentRead(true).build()).item();
        if (existing != null && !existing.isEmpty()) {
            requireSamePage(existing, projection, pageNumber);
            return;
        }
        try {
            client.putItem(PutItemRequest.builder().tableName(tableName).item(expected)
                    .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build());
        } catch (ConditionalCheckFailedException racedWrite) {
            Map<String, AttributeValue> afterRace = client.getItem(GetItemRequest.builder().tableName(tableName)
                    .key(key).consistentRead(true).build()).item();
            if (afterRace == null || afterRace.isEmpty()) throw racedWrite;
            requireSamePage(afterRace, projection, pageNumber);
        }
    }

    private static void requireSamePage(Map<String, AttributeValue> item,
            KwV1MemoryProjectionBuilder.Projection projection, int pageNumber) {
        var persisted = DynamoDbMemorySearchTermPageMapper.read(item, projection.memoryId(),
                projection.semanticVersion(), projection.analyzerVersion(), projection.tokenKeyVersion(), pageNumber);
        if (!persisted.descriptors().equals(projection.pages().get(pageNumber).terms())) {
            throw new IllegalStateException("persisted projection page conflicts with deterministic rebuild");
        }
    }

    /** Reads every expected page using DynamoDB's strongly consistent GetItem path. */
    public List<DynamoDbMemorySearchTermPageMapper.PersistedPage> readAll(
            KwV1MemoryProjectionBuilder.Projection expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        List<DynamoDbMemorySearchTermPageMapper.PersistedPage> pages = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < expected.pages().size(); pageNumber++) {
            Map<String, AttributeValue> key = Map.of(
                    "pk", AttributeValue.builder().s("MEMORY_SEARCH#PROJECTION#" + expected.memoryId().value()).build(),
                    "sk", AttributeValue.builder().s("VERSION#%020d#TERMS#%04d"
                            .formatted(expected.semanticVersion(), pageNumber)).build());
            Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                    .tableName(tableName).key(key).consistentRead(true).build()).item();
            if (item == null || item.isEmpty()) {
                throw new IllegalStateException("staged term page is missing");
            }
            pages.add(DynamoDbMemorySearchTermPageMapper.read(item, expected.memoryId(),
                    expected.semanticVersion(), expected.analyzerVersion(), expected.tokenKeyVersion(), pageNumber));
        }
        return List.copyOf(pages);
    }
}
