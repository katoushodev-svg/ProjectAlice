package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class DynamoDbMemorySearchPostingStoreTest {

    @Test
    void persistsContentFreeCurrentAndHistoricalPostingItemsWithDeterministicKeys() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        when(client.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(Map.of()).build());
        DynamoDbMemorySearchPostingStore store = new DynamoDbMemorySearchPostingStore(client, "alice-memory-test");
        MemoryId memoryId = new MemoryId("7f1f8e2a-4b3c-4d5e-8f60-123456789abc");
        String digest = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

        store.putCurrent(1, digest, memoryId, 3, KwV1Analyzer.TermKind.LEXICAL);
        store.putHistorical(1, digest, memoryId, 2, KwV1Analyzer.TermKind.NGRAM_2);

        ArgumentCaptor<PutItemRequest> requests = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client, org.mockito.Mockito.times(2)).putItem(requests.capture());
        var current = requests.getAllValues().get(0);
        var historical = requests.getAllValues().get(1);
        assertEquals("alice-memory-test", current.tableName());
        assertEquals("MEMORY_SEARCH#TERM#1#CURRENT#" + digest, current.item().get("pk").s());
        assertEquals("MEMORY#" + memoryId.value(), current.item().get("sk").s());
        assertEquals("MEMORY_SEARCH#TERM#1#HISTORICAL#" + digest, historical.item().get("pk").s());
        assertEquals("MEMORY#" + memoryId.value() + "#REVISION#00000000000000000002",
                historical.item().get("sk").s());
    }
}
