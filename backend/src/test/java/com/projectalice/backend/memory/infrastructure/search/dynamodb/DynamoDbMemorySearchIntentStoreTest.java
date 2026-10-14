package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

class DynamoDbMemorySearchIntentStoreTest {
    private static final String OWNER_1 = "00000000-0000-4000-8000-000000000001";
    private static final String OWNER_2 = "00000000-0000-4000-8000-000000000002";

    @Test
    void claimUsesAtomicOldKeyDeleteAndFencedNewKeyPut() {
        var captured = new AtomicReference<TransactWriteItemsRequest>();
        var store = new DynamoDbMemorySearchIntentStore(client(captured), "alice");

        var claim = store.claimAvailable(available(), OWNER_1, 1_000L);

        assertEquals(1, claim.epoch());
        assertEquals(301_000L, claim.leaseExpiresAtEpochMillis());
        var actions = captured.get().transactItems();
        assertEquals(2, actions.size());
        assertTrue(actions.get(0).delete().conditionExpression().contains("attribute_not_exists(claimOwnerId)"));
        assertTrue(!actions.get(0).delete().expressionAttributeValues().containsKey(":now"));
        assertTrue(actions.get(1).put().item().get("sk").s().startsWith("CLAIMED#0000000301000#"));
        assertEquals("CLAIMED", actions.get(1).put().item().get("status").s());
    }

    @Test
    void takeoverIncrementsEpochAndChecksExpiredPriorOwner() {
        var captured = new AtomicReference<TransactWriteItemsRequest>();
        var store = new DynamoDbMemorySearchIntentStore(client(captured), "alice");
        var expired = new java.util.HashMap<>(available());
        expired.put("sk", s("CLAIMED#0000000301000#0000000000000#7f1f8e2a-4b3c-4d5e-8f60-123456789abc#00000000000000000001"));
        expired.put("claimOwnerId", s(OWNER_1));
        expired.put("claimEpoch", n(8));
        expired.put("leaseExpiresAtEpochMillis", n(3_010_000));
        expired.put("status", s("CLAIMED"));

        var takeover = store.takeOver(expired, OWNER_2, 3_010_000L);

        assertEquals(9, takeover.epoch());
        assertTrue(captured.get().transactItems().get(0).delete().conditionExpression()
                .contains("leaseExpiresAtEpochMillis <= :now"));
        assertTrue(!captured.get().transactItems().get(0).delete().expressionAttributeValues().containsKey(":attempt"));
        assertEquals(OWNER_2, captured.get().transactItems().get(1).put().item().get("claimOwnerId").s());
    }

    @Test
    void activationAtomicallyPutsMetadataAndDeletesCurrentClaimWithOwnerEpochLeaseChecks() {
        var captured = new AtomicReference<TransactWriteItemsRequest>();
        var store = new DynamoDbMemorySearchIntentStore(client(captured), "alice");
        var claim = store.claimAvailable(available(), OWNER_1, 1_000L);
        store.activate(claim, Map.of("pk", s("MEMORY_SEARCH#PROJECTION#7f1f8e2a-4b3c-4d5e-8f60-123456789abc"),
                "sk", s("META"), "activeSemanticVersion", n(1)), 2_000L);

        var actions = captured.get().transactItems();
        assertEquals(2, actions.size());
        assertTrue(actions.get(0).put().conditionExpression().contains("activeSemanticVersion < :target"));
        var delete = actions.get(1).delete();
        assertTrue(delete.conditionExpression().contains("claimOwnerId = :owner"));
        assertTrue(delete.conditionExpression().contains("claimEpoch = :epoch"));
        assertTrue(delete.conditionExpression().contains("leaseExpiresAtEpochMillis > :now"));
    }

    @Test
    void retryAtomicallyDefersIntentAndPreservesIdentityAndEpoch() {
        var captured = new AtomicReference<TransactWriteItemsRequest>();
        var store = new DynamoDbMemorySearchIntentStore(client(captured), "alice");
        var claim = store.claimAvailable(available(), OWNER_1, 1_000L);

        store.retry(claim, 5_000L, 2_000L);

        var actions = captured.get().transactItems();
        assertEquals(2, actions.size());
        var delete = actions.get(0).delete();
        assertTrue(delete.conditionExpression().contains("claimOwnerId = :owner"));
        assertTrue(delete.conditionExpression().contains("claimEpoch = :epoch"));
        assertTrue(delete.conditionExpression().contains("leaseExpiresAtEpochMillis > :now"));
        assertEquals("1", delete.expressionAttributeValues().get(":epoch").n());
        var put = actions.get(1).put().item();
        assertEquals("AVAILABLE#0000000005000#0000000000000#7f1f8e2a-4b3c-4d5e-8f60-123456789abc#00000000000000000001",
                put.get("sk").s());
        assertEquals("00000000-0000-4000-8000-000000000003", put.get("intentId").s());
        assertEquals("1", put.get("claimEpoch").n());
        assertEquals("1", put.get("attemptCount").n());
        assertEquals("5000", put.get("nextAttemptAt").n());
        assertTrue(!put.containsKey("claimOwnerId"));
        assertTrue(!put.containsKey("leaseExpiresAtEpochMillis"));
    }

    private static DynamoDbClient client(AtomicReference<TransactWriteItemsRequest> captured) {
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("transactWriteItems")
                            && args != null && args[0] instanceof TransactWriteItemsRequest request) {
                        captured.set(request);
                        return TransactWriteItemsResponse.builder().build();
                    }
                    if (method.getName().equals("serviceName")) return "DynamoDb";
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("toString")) return "DynamoDbClientTestProxy";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Map<String, AttributeValue> available() {
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
