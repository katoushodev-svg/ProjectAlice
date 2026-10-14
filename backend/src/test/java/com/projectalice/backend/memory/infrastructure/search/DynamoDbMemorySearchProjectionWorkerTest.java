package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchPostingStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageStore;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

class DynamoDbMemorySearchProjectionWorkerTest {
    @Test
    void claimRaceDoesNotProcessIntent() {
        var queryCount = new AtomicInteger();
        DynamoDbClient client = (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("query") && args[0] instanceof QueryRequest) {
                        return QueryResponse.builder().items(queryCount.getAndIncrement() == 0
                                ? java.util.List.of(availableIntent()) : java.util.List.of()).build();
                    }
                    if (method.getName().equals("transactWriteItems"))
                        throw TransactionCanceledException.builder().message("claim raced").build();
                    if (method.getName().equals("toString")) return "DynamoDbClientTestProxy";
                    throw new UnsupportedOperationException(method.getName());
                });
        var termKeys = new MemorySearchTermDigest(Map.of(1,
                "term-purpose-test-key".getBytes(StandardCharsets.UTF_8)), 1);
        var projectionKeys = new MemoryProjectionDigest(new MemoryProjectionDigestCanonicalEncoder(), Map.of(1,
                "projection-purpose-test-key".getBytes(StandardCharsets.UTF_8)), 1);
        var worker = new DynamoDbMemorySearchProjectionWorker(client, "alice",
                new DynamoDbMemorySearchIntentStore(client, "alice"),
                new DynamoDbMemorySearchTermPageStore(client, "alice"),
                new DynamoDbMemorySearchPostingStore(client, "alice"),
                new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), termKeys), projectionKeys);

        assertFalse(worker.processOne("00000000-0000-4000-8000-000000000001", 2_000L));
    }

    private static Map<String, AttributeValue> availableIntent() {
        return Map.of("pk", s("MEMORY_SEARCH#PENDING"),
                "sk", s("AVAILABLE#0000000001000#0000000000000#7f1f8e2a-4b3c-4d5e-8f60-123456789abc#00000000000000000001"),
                "itemType", s("MEMORY_SEARCH_INTENT"), "operation", s("UPSERT"),
                "intentId", s("00000000-0000-4000-8000-000000000003"),
                "memoryId", s("7f1f8e2a-4b3c-4d5e-8f60-123456789abc"),
                "targetVersion", n(1), "createdAt", s(OffsetDateTime.parse("1970-01-01T00:00:00Z").toString()),
                "attemptCount", n(0), "nextAttemptAt", n(1_000));
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
