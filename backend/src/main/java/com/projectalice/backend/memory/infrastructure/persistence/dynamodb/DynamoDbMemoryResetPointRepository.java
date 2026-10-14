package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.application.port.out.MemoryResetPointRepository;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/** Strongly reads the accepted MEMORY_SETTINGS#PRIMARY / RESET_POINT item. */
public final class DynamoDbMemoryResetPointRepository implements MemoryResetPointRepository {
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbMemoryResetPointRepository(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    @Override
    public long currentGeneration() {
        var item = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(Map.of("pk", s("MEMORY_SETTINGS#PRIMARY"), "sk", s("RESET_POINT")))
                .consistentRead(true).build()).item();
        if (item == null || item.isEmpty()) throw new IllegalStateException("Memory Reset Point is missing");
        if (!"MEMORY_RESET_POINT".equals(string(item, "itemType")) || number(item, "schemaVersion") != 1)
            throw new IllegalStateException("Memory Reset Point has an unsupported schema");
        long generation = number(item, "generation");
        if (generation < 0) throw new IllegalStateException("Memory Reset Point generation is invalid");
        return generation;
    }

    private static String string(Map<String, AttributeValue> item, String field) {
        var value = item.get(field);
        if (value == null || value.s() == null) throw new IllegalStateException("Memory Reset Point is missing " + field);
        return value.s();
    }

    private static long number(Map<String, AttributeValue> item, String field) {
        var value = item.get(field);
        if (value == null || value.n() == null) throw new IllegalStateException("Memory Reset Point is missing " + field);
        try { return Long.parseLong(value.n()); }
        catch (NumberFormatException invalid) { throw new IllegalStateException("Memory Reset Point has invalid " + field, invalid); }
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
}
