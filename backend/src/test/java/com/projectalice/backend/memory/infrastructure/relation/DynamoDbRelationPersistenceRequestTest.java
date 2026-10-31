package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.ItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

class DynamoDbRelationPersistenceRequestTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void nonceReservationUsesAtomicConditionalCounterAndReturnsBound96BitNonce() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        var version = Map.of("noncePrefix", s("A1B2Cw"), "nextNonceCounter", n("17"));
        when(client.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder()
                .attributes(Map.of("versions", AttributeValue.builder().m(Map.of("4",
                        AttributeValue.builder().m(version).build())).build())).build());

        byte[] nonce = new DynamoDbRelationNonceProvider(client, "memory").reserveNonce(4);

        assertEquals(12, nonce.length);
        assertEquals("0350760b0000000000000011", java.util.HexFormat.of().formatHex(nonce));
        var request = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(client).updateItem(request.capture());
        assertTrue(request.getValue().conditionExpression().contains(":writeActive"));
        assertTrue(request.getValue().expressionAttributeValues().values().stream()
                .anyMatch(value -> "WRITE_ACTIVE".equals(value.s())));
        assertTrue(request.getValue().expressionAttributeNames().containsValue("nextNonceCounter"));
        assertTrue(request.getValue().updateExpression().contains("if_not_exists"));
        assertTrue(request.getValue().updateExpression().contains("controlVersion"));
    }

    @Test
    void controlInitializationIsOneConditionalTransaction() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        var repository = new DynamoDbRelationControlRepository(client, "memory", CLOCK);

        var key = new javax.crypto.spec.SecretKeySpec(new byte[32], "AES");
        assertTrue(repository.initializeAfterKeyProbe(new RelationStateKeyProvider() {
            @Override public void probeRelationPurpose() { }
            @Override public KeyMaterial activeEncryptionKey() { return new KeyMaterial(2, key); }
            @Override public javax.crypto.SecretKey decryptionKey(int version) { return key; }
        }));

        var request = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(request.capture());
        assertEquals(2, request.getValue().transactItems().size());
        assertTrue(request.getValue().transactItems().stream()
                .allMatch(item -> item.put().conditionExpression().contains("attribute_not_exists")));
        var keyItem = request.getValue().transactItems().get(1).put().item();
        var version = keyItem.get("versions").m().get("2").m();
        assertEquals(4, java.util.Base64.getUrlDecoder().decode(version.get("noncePrefix").s()).length);
        assertEquals("0", version.get("nextNonceCounter").n());
        assertTrue(!keyItem.containsKey("keyMaterial") && !keyItem.containsKey("secretHandle"));
    }

    @Test
    void preparingReferenceAndKeyCountUseOneTransactionAndCollisionIsNotSuccess() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        var reference = new RelationKeyReference(new RelationKeyReference.Key(4,
                "8a1f8e2a-4b3c-4d5e-8f60-123456789abc"),
                RelationKeyReference.State.PREPARING, 1, Instant.parse("2026-10-29T00:00:00Z").atOffset(ZoneOffset.UTC));
        var store = new DynamoDbRelationKeyReferenceStore(client, "memory", CLOCK);

        assertEquals(RelationKeyReferenceStore.CreateResult.CREATED, store.createPreparingIfAbsent(reference));
        var request = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(request.capture());
        assertEquals(2, request.getValue().transactItems().size());
        assertTrue(request.getValue().transactItems().get(0).update().expressionAttributeNames()
                .containsValue("preparingReferenceCount"));
        assertTrue(request.getValue().transactItems().get(1).put().conditionExpression()
                .contains("attribute_not_exists"));
        var item = request.getValue().transactItems().get(1).put().item();
        assertEquals("PREPARING", item.get("referenceState").s());
        assertTrue(!item.containsKey("candidate") && !item.containsKey("memoryId")
                && !item.containsKey("ciphertext") && !item.containsKey("nonce"));
    }

    @Test
    void failedPrepareTransactionCannotReturnSuccessWhenStrongReadFindsNoReference() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class))).thenThrow(
                TransactionCanceledException.builder().message("condition failed").build());
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(Map.of()).build());
        var reference = new RelationKeyReference(new RelationKeyReference.Key(4,
                "8a1f8e2a-4b3c-4d5e-8f60-123456789abc"),
                RelationKeyReference.State.PREPARING, 1, Instant.parse("2026-10-29T00:00:00Z").atOffset(ZoneOffset.UTC));

        assertThrows(RelationControlUnavailableException.class,
                () -> new DynamoDbRelationKeyReferenceStore(client, "memory", CLOCK)
                        .createPreparingIfAbsent(reference));
        var request = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(client).getItem(request.capture());
        assertTrue(request.getValue().consistentRead());
    }

    @Test
    void controlSnapshotUsesAtomicStrongReadAndFailsClosedOnGlobalKeyCountMismatch() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        Map<String, AttributeValue> global = Map.of("schemaVersion", n("1"),
                "itemType", s("MEMORY_RELATION_REVIEW_CONTROL"), "activeCount", n("1"),
                "maxActiveCount", n("20"), "controlVersion", n("3"));
        Map<String, AttributeValue> keyVersion = Map.of("state", s("WRITE_ACTIVE"),
                "noncePrefix", s("A1B2Cw"), "nextNonceCounter", n("5"),
                "activeReviewCount", n("0"), "preparingReferenceCount", n("0"),
                "cleanupPendingCount", n("0"));
        Map<String, AttributeValue> key = Map.of("schemaVersion", n("1"),
                "itemType", s("MEMORY_RELATION_KEY_CONTROL"), "writeKeyVersion", n("2"),
                "maxActiveKeyVersions", n("2"), "controlVersion", n("4"),
                "versions", AttributeValue.builder().m(Map.of("2", AttributeValue.builder()
                        .m(keyVersion).build())).build());
        when(client.transactGetItems(any(TransactGetItemsRequest.class))).thenReturn(
                TransactGetItemsResponse.builder().responses(List.of(
                        ItemResponse.builder().item(global).build(), ItemResponse.builder().item(key).build())).build());

        assertThrows(RelationControlUnavailableException.class,
                () -> new DynamoDbRelationControlRepository(client, "memory", CLOCK).readControls());
        var request = ArgumentCaptor.forClass(TransactGetItemsRequest.class);
        verify(client).transactGetItems(request.capture());
        assertEquals(2, request.getValue().transactItems().size());
        assertEquals(DynamoDbRelationControlRepository.GLOBAL_PK,
                request.getValue().transactItems().get(0).get().key().get("pk").s());
        assertEquals(DynamoDbRelationControlRepository.KEY_PK,
                request.getValue().transactItems().get(1).get().key().get("pk").s());
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
