package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/** Writes deterministic posting items; activation/readiness remains the projection worker's concern. */
public final class DynamoDbMemorySearchPostingStore {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbMemorySearchPostingStore(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
    }

    public void putCurrent(
            int keyVersion,
            String termDigest,
            MemoryId memoryId,
            long semanticVersion,
            KwV1Analyzer.TermKind termKind) {
        Map<String, AttributeValue> expected = DynamoDbMemorySearchPostingMapper.current(
                keyVersion, termDigest, memoryId, semanticVersion, termKind);
        Map<String, AttributeValue> key = Map.of("pk", expected.get("pk"), "sk", expected.get("sk"));
        for (int attempt = 0; attempt < 3; attempt++) {
            Map<String, AttributeValue> existing = client.getItem(GetItemRequest.builder().tableName(tableName)
                    .key(key).consistentRead(true).build()).item();
            if (existing == null || existing.isEmpty()) {
                try {
                    client.putItem(PutItemRequest.builder().tableName(tableName).item(expected)
                            .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build());
                    return;
                } catch (ConditionalCheckFailedException racedWrite) {
                    continue;
                }
            }
            long existingVersion = number(existing, "semanticVersion");
            if (existingVersion > semanticVersion) return;
            if (existingVersion == semanticVersion) {
                if (!existing.equals(expected)) throw new IllegalStateException("current posting conflicts with deterministic rebuild");
                return;
            }
            try {
                client.putItem(PutItemRequest.builder().tableName(tableName).item(expected)
                        .conditionExpression("semanticVersion = :existing")
                        .expressionAttributeValues(Map.of(":existing", numberValue(existingVersion))).build());
                return;
            } catch (ConditionalCheckFailedException racedUpdate) {
                // Re-read and either accept a newer value or retry against the current version.
            }
        }
        throw new IllegalStateException("current posting changed repeatedly during deterministic write");
    }

    public void putHistorical(
            int keyVersion,
            String termDigest,
            MemoryId memoryId,
            long revisionNumber,
            KwV1Analyzer.TermKind termKind) {
        Map<String, AttributeValue> expected = DynamoDbMemorySearchPostingMapper.historical(
                keyVersion, termDigest, memoryId, revisionNumber, termKind);
        Map<String, AttributeValue> key = Map.of("pk", expected.get("pk"), "sk", expected.get("sk"));
        Map<String, AttributeValue> existing = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(key).consistentRead(true).build()).item();
        if (existing != null && !existing.isEmpty()) {
            if (!existing.equals(expected)) throw new IllegalStateException("historical posting conflicts with deterministic rebuild");
            return;
        }
        try {
            client.putItem(PutItemRequest.builder().tableName(tableName).item(expected)
                    .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build());
        } catch (ConditionalCheckFailedException racedWrite) {
            Map<String, AttributeValue> afterRace = client.getItem(GetItemRequest.builder().tableName(tableName)
                    .key(key).consistentRead(true).build()).item();
            if (afterRace == null || !afterRace.equals(expected)) {
                throw new IllegalStateException("historical posting conflicts with concurrent deterministic write", racedWrite);
            }
        }
    }

    /** Deletes a prior Current Posting only if it still belongs to the expected active version. */
    public void deleteCurrentIfVersion(int keyVersion, String termDigest, MemoryId memoryId, long expectedVersion) {
        Map<String, AttributeValue> key = Map.of(
                "pk", AttributeValue.builder().s("MEMORY_SEARCH#TERM#" + keyVersion + "#CURRENT#" + termDigest).build(),
                "sk", AttributeValue.builder().s("MEMORY#" + memoryId.value()).build());
        Map<String, AttributeValue> existing = client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(key).consistentRead(true).build()).item();
        if (existing == null || existing.isEmpty() || number(existing, "semanticVersion") != expectedVersion) return;
        try {
            client.deleteItem(DeleteItemRequest.builder().tableName(tableName).key(key)
                    .conditionExpression("semanticVersion = :version")
                    .expressionAttributeValues(Map.of(":version", numberValue(expectedVersion))).build());
        } catch (ConditionalCheckFailedException concurrentReplacement) {
            // A later projection replaced this key; retaining it is safer than deleting the newer value.
        }
    }

    private static long number(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalStateException("posting has no numeric " + name);
        return Long.parseLong(value.n());
    }

    private static AttributeValue numberValue(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
