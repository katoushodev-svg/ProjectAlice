package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import org.junit.jupiter.api.Test;

class DynamoDbMemoryResetPointRepositoryTest {
    @Test
    void stronglyReadsGenerationFromTheAcceptedResetPointKey() {
        GetItemRequest[] captured = new GetItemRequest[1];
        DynamoDbClient client = client((request) -> {
            captured[0] = request;
            return GetItemResponse.builder().item(Map.of(
                    "itemType", s("MEMORY_RESET_POINT"), "schemaVersion", n("1"), "generation", n("3"))).build();
        });

        assertEquals(3, new DynamoDbMemoryResetPointRepository(client, "memories").currentGeneration());
        assertEquals("MEMORY_SETTINGS#PRIMARY", captured[0].key().get("pk").s());
        assertEquals("RESET_POINT", captured[0].key().get("sk").s());
        assertTrue(captured[0].consistentRead());
    }

    @Test
    void failsClosedWhenResetPointIsMissingOrMalformed() {
        DynamoDbClient missing = client(request -> GetItemResponse.builder().item(Map.of()).build());
        assertThrows(IllegalStateException.class,
                () -> new DynamoDbMemoryResetPointRepository(missing, "memories").currentGeneration());
        DynamoDbClient malformed = client(request -> GetItemResponse.builder().item(Map.of(
                "itemType", s("OTHER"), "schemaVersion", n("1"), "generation", n("3"))).build());
        assertThrows(IllegalStateException.class,
                () -> new DynamoDbMemoryResetPointRepository(malformed, "memories").currentGeneration());
    }

    private static DynamoDbClient client(java.util.function.Function<GetItemRequest, GetItemResponse> getter) {
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getItem") && args[0] instanceof GetItemRequest request)
                        return getter.apply(request);
                    if (method.getName().equals("serviceName")) return "DynamoDb";
                    if (method.getName().equals("close")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
