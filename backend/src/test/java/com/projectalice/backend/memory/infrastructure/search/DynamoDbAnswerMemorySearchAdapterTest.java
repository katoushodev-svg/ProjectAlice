package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.MemoryContextBudget;
import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemorySearchSignal;
import com.projectalice.backend.memory.application.MemoryTemporalIntent;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import org.junit.jupiter.api.Test;

class DynamoDbAnswerMemorySearchAdapterTest {
    private static final String MEMORY_ID = "7f1f8e2a-4b3c-4d5e-8f60-123456789abc";
    private static final byte[] KEY = "synthetic-memory-search-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void returnsReadyCandidatesWithoutAssigningARelevanceOrder() {
        MemorySearchTermDigest digest = new MemorySearchTermDigest(Map.of(1, KEY), 1);
        String targetPk = "MEMORY_SEARCH#TERM#1#CURRENT#"
                + digest.digestForWrite("KW_V1", "札幌");
        AtomicInteger candidateQueries = new AtomicInteger();
        DynamoDbClient client = client(request -> {
            String pk = request.expressionAttributeValues().get(":pk").s();
            if (pk.equals("MEMORY_SEARCH#PENDING")) return QueryResponse.builder().items(List.of()).build();
            if (pk.equals(targetPk)) {
                candidateQueries.incrementAndGet();
                return QueryResponse.builder().items(List.of(Map.of(
                        "memoryId", s(MEMORY_ID), "semanticVersion", n("1")))).build();
            }
            return QueryResponse.builder().items(List.of()).build();
        }, request -> GetItemResponse.builder().item(Map.of(
                "itemType", s("MEMORY_SEARCH_PROJECTION"), "schemaVersion", n("1"), "analyzerVersion", s("KW_V1"),
                "status", s("READY"),
                "activeSemanticVersion", n("1"), "tokenKeyVersion", n("1"),
                "projectionDigest", s("A".repeat(43)), "projectionDigestKeyVersion", n("1"),
                "fallbackRequired", AttributeValue.builder().bool(false).build())).build());
        var adapter = new DynamoDbAnswerMemorySearchAdapter(client, "memories", Optional.of(digest),
                new DynamoDbMemorySearchIntentStore(client, "memories"));

        var result = adapter.findCandidates(query());

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.candidates().size());
        assertEquals(MEMORY_ID, result.candidates().getFirst().memoryId().value());
        assertEquals(1, candidateQueries.get());
        assertTrue(result.candidates().getFirst().signals().contains(MemorySearchSignal.KEYWORD_MATCH));
    }

    @Test
    void missingKeyCapabilityReturnsUnavailableWithoutSearching() {
        AtomicInteger queries = new AtomicInteger();
        DynamoDbClient client = client(request -> { queries.incrementAndGet(); return QueryResponse.builder().build(); },
                request -> GetItemResponse.builder().build());
        var adapter = new DynamoDbAnswerMemorySearchAdapter(client, "memories", Optional.empty(),
                new DynamoDbMemorySearchIntentStore(client, "memories"));

        var result = adapter.findCandidates(query());

        assertEquals(SearchCompleteness.UNAVAILABLE, result.completeness());
        assertTrue(result.candidates().isEmpty());
        assertEquals(0, queries.get());
    }

    @Test
    void pendingProjectionIntentMakesAnEmptyCandidateSetPartial() {
        MemorySearchTermDigest digest = new MemorySearchTermDigest(Map.of(1, KEY), 1);
        DynamoDbClient client = client(request -> {
            String pk = request.expressionAttributeValues().get(":pk").s();
            if (pk.equals("MEMORY_SEARCH#PENDING"))
                return QueryResponse.builder().items(List.of(Map.of("intentId", s("pending")))).build();
            return QueryResponse.builder().items(List.of()).build();
        }, request -> GetItemResponse.builder().build());
        var adapter = new DynamoDbAnswerMemorySearchAdapter(client, "memories", Optional.of(digest),
                new DynamoDbMemorySearchIntentStore(client, "memories"));

        var result = adapter.findCandidates(query());

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertTrue(result.candidates().isEmpty());
    }

    private static DynamoDbClient client(java.util.function.Function<QueryRequest, QueryResponse> query,
            java.util.function.Function<GetItemRequest, GetItemResponse> get) {
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("query") && args[0] instanceof QueryRequest request) return query.apply(request);
                    if (method.getName().equals("getItem") && args[0] instanceof GetItemRequest request) return get.apply(request);
                    if (method.getName().equals("serviceName")) return "DynamoDb";
                    if (method.getName().equals("close")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static MemoryRetrievalQuery query() {
        return new MemoryRetrievalQuery(MemoryRetrievalPurpose.ANSWER_CURRENT, "札幌", List.of(), List.of(), List.of(),
                java.util.Set.of(), MemoryTemporalIntent.UNSPECIFIED, MemoryContextBudget.baseline(), "req");
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
