package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import com.projectalice.backend.memory.infrastructure.search.MemorySearchTermDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoDbMemorySearchTermPageStoreTest {
    @Test
    void writesExistingPageShapeAndReadsBackWithStrongConsistency() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        var projection = new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), new MemorySearchTermDigest(
                Map.of(2, "term-test-key".getBytes(StandardCharsets.UTF_8)), 2))
                .build(new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc"), 7,
                        new MemoryContent("alpha beta"));
        var store = new DynamoDbMemorySearchTermPageStore(client, "alice-test");
        var item = DynamoDbMemorySearchTermPageMapper.map(projection, 0);

        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(item).build());
        store.put(projection, 0);
        var pages = store.readAll(projection);

        assertEquals(1, pages.size());
        assertEquals(projection.pages().get(0).terms(), pages.get(0).descriptors());
        verify(client).putItem(argThat((software.amazon.awssdk.services.dynamodb.model.PutItemRequest request) -> request.tableName().equals("alice-test")
                && request.item().equals(item)));
        verify(client).getItem(argThat((GetItemRequest request) -> Boolean.TRUE.equals(request.consistentRead())
                && request.key().get("pk").s().equals(
                        "MEMORY_SEARCH#PROJECTION#7f1f8e2a-4b3c-4d5e-8f60-123456789abc")));
    }
}
