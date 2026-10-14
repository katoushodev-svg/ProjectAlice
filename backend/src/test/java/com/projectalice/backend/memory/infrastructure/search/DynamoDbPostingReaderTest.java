package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamoDbPostingReaderTest {

    private static final String DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private final DynamoDbClient client = mock(DynamoDbClient.class);
    private final DynamoDbPostingReader reader = new DynamoDbPostingReader(client, "alice-table");

    @Test
    void readsCurrentPartitionWithExpectedKeyAndStrongConsistency() {
        when(client.query(any(QueryRequest.class))).thenAnswer(invocation -> {
            QueryRequest request = invocation.getArgument(0);
            assertEquals("alice-table", request.tableName());
            assertEquals("pk = :pk", request.keyConditionExpression());
            assertEquals("MEMORY_SEARCH#TERM#1#CURRENT#" + DIGEST,
                    request.expressionAttributeValues().get(":pk").s());
            assertTrue(request.consistentRead());
            assertEquals(1_000, request.limit());
            return QueryResponse.builder().items(List.of(posting("00000000-0000-4000-8000-000000000001",
                    "LEXICAL", null))).build();
        });

        var result = reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(1, result.postingEvaluations());
        assertNull(result.postings().getFirst().revisionNumber());
        assertEquals(7L, result.postings().getFirst().semanticVersion());
        verify(client, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void historicalPurposeReadsCurrentAndHistoricalPartitions() {
        when(client.query(any(QueryRequest.class))).thenAnswer(invocation -> {
            QueryRequest request = invocation.getArgument(0);
            String pk = request.expressionAttributeValues().get(":pk").s();
            if (pk.contains("#HISTORICAL#")) {
                return QueryResponse.builder().items(List.of(posting(
                        "00000000-0000-4000-8000-000000000001", "NGRAM_2", "3"))).build();
            }
            return QueryResponse.builder().items(List.of(posting(
                    "00000000-0000-4000-8000-000000000001", "LEXICAL", null))).build();
        });

        var result = reader.read(MemoryRetrievalPurpose.ANSWER_HISTORICAL,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(2, result.postingEvaluations());
        assertNull(result.postings().get(0).revisionNumber());
        assertEquals(3L, result.postings().get(1).revisionNumber());
        verify(client, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void currentOnlyPurposesDoNotQueryHistoricalPartition() {
        when(client.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of()).build());

        reader.read(MemoryRetrievalPurpose.RELATION_DECISION,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        verify(client, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void followsLastEvaluatedKeyUntilStreamIsComplete() {
        Map<String, AttributeValue> lastKey = Map.of(
                "pk", AttributeValue.builder().s("some-pk").build(),
                "sk", AttributeValue.builder().s("MEMORY#some-id").build());

        when(client.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder()
                        .items(List.of(posting("00000000-0000-4000-8000-000000000001", "LEXICAL", null)))
                        .lastEvaluatedKey(lastKey)
                        .build(),
                QueryResponse.builder()
                        .items(List.of(posting("00000000-0000-4000-8000-000000000002", "NGRAM_3", null)))
                        .build());

        var result = reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(2, result.postingEvaluations());
        verify(client, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void returnsPartialWhenPostingLimitLeavesMoreResults() {
        when(client.query(any(QueryRequest.class))).thenAnswer(invocation -> {
            QueryRequest request = invocation.getArgument(0);
            assertEquals(1_000, request.limit());
            var items = new java.util.ArrayList<Map<String, AttributeValue>>();
            for (int i = 1; i <= 1_000; i++) {
                items.add(posting("00000000-0000-4000-8000-" + String.format("%012d", i),
                        "NGRAM_1", null));
            }
            return QueryResponse.builder()
                    .items(items)
                    .lastEvaluatedKey(Map.of(
                            "pk", AttributeValue.builder().s("some-pk").build(),
                            "sk", AttributeValue.builder().s("MEMORY#last").build()))
                    .build();
        });

        var result = reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)));

        assertEquals(SearchCompleteness.PARTIAL, result.completeness());
        assertEquals(1_000, result.postingEvaluations());
        assertTrue(result.hasMore());
        verify(client, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void validatesDigestAndQueryTermCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new DynamoDbPostingReader.QueryTerm(1, "bad", 0));
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                        java.util.Collections.nCopies(33,
                                new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0))));
    }

    @Test
    void currentPostingWithoutSemanticVersionIsRejected() {
        Map<String, AttributeValue> item = Map.of(
                "itemType", AttributeValue.builder().s("MEMORY_SEARCH_POSTING").build(),
                "memoryId", AttributeValue.builder().s("00000000-0000-4000-8000-000000000001").build(),
                "termKind", AttributeValue.builder().s("LEXICAL").build());
        when(client.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of(item)).build());

        assertThrows(IllegalStateException.class,
                () -> reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                        List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0))));
    }

    @Test
    void malformedPostingDoesNotBecomeAnEmptyCompleteResult() {
        when(client.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of(Map.of(
                        "itemType", AttributeValue.builder().s("WRONG_TYPE").build(),
                        "memoryId", AttributeValue.builder().s(
                                "00000000-0000-4000-8000-000000000001").build(),
                        "termKind", AttributeValue.builder().s("LEXICAL").build()))).build());

        assertThrows(IllegalStateException.class,
                () -> reader.read(MemoryRetrievalPurpose.ANSWER_CURRENT,
                        List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0))));
    }

    private static Map<String, AttributeValue> posting(String memoryId, String termKind, String revision) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("itemType", AttributeValue.builder().s("MEMORY_SEARCH_POSTING").build());
        item.put("memoryId", AttributeValue.builder().s(memoryId).build());
        item.put("termKind", AttributeValue.builder().s(termKind).build());
        if (revision != null) {
            item.put("revisionNumber", AttributeValue.builder().n(revision).build());
        } else {
            item.put("semanticVersion", AttributeValue.builder().n("7").build());
        }
        return item;
    }
}
