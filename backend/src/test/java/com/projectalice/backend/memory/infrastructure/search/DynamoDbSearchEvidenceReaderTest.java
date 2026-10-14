package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.projectalice.backend.memory.domain.MemoryId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoDbSearchEvidenceReaderTest {

    private static final MemoryId ID =
            new MemoryId("123e4567-e89b-42d3-a456-426614174000");
    private DynamoDbClient client;
    private DynamoDbSearchEvidenceReader reader;

    @BeforeEach
    void setUp() {
        client = mock(DynamoDbClient.class);
        reader = new DynamoDbSearchEvidenceReader(client, "alice-memory");
    }

    @Test
    void readsProjectionMetadataUsingStrongConsistentRead() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("MEMORY_SEARCH_PROJECTION"),
                        "schemaVersion", n("1"),
                        "memoryId", s(ID.value()),
                        "activeSemanticVersion", n("4"),
                        "analyzerVersion", s("KW_V1"),
                        "tokenKeyVersion", n("1"),
                        "status", s("READY"),
                        "fallbackRequired", b(false)))
                .build());

        var result = reader.findCurrentProjection(ID);

        assertTrue(result.isPresent());
        assertEquals(4, result.get().activeSemanticVersion());
        assertTrue(result.get().readyFor("KW_V1"));
        assertFalse(result.get().fallbackRequired());

        var captor = org.mockito.ArgumentCaptor.forClass(GetItemRequest.class);
        verify(client).getItem(captor.capture());
        GetItemRequest request = captor.getValue();
        assertTrue(request.consistentRead());
        assertEquals("alice-memory", request.tableName());
        assertEquals("MEMORY_SEARCH#PROJECTION#" + ID.value(), request.key().get("pk").s());
        assertEquals("META", request.key().get("sk").s());
    }

    @Test
    void missingProjectionReturnsEmpty() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertEquals(Optional.empty(), reader.findCurrentProjection(ID));
    }

    @Test
    void rejectsMalformedProjectionInsteadOfTreatingItAsReady() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("WRONG_TYPE"),
                        "schemaVersion", n("1")))
                .build());
        assertThrows(IllegalStateException.class, () -> reader.findCurrentProjection(ID));
    }

    @Test
    void readsResetPointAndFailsClosedWhileResetIsPending() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("MEMORY_RESET_POINT"),
                        "schemaVersion", n("1"),
                        "generation", n("7"),
                        "deletionBoundaryVersion", n("103"),
                        "pendingReset", m(Map.of("operationId", s("opaque")))))
                .build());

        var result = reader.findResetPoint().orElseThrow();

        assertEquals(7, result.generation());
        assertEquals(103, result.deletionBoundaryVersion());
        assertTrue(result.resetInProgress());
    }

    @Test
    void nullPendingResetMeansNoResetInProgress() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("MEMORY_RESET_POINT"),
                        "schemaVersion", n("1"),
                        "generation", n("7"),
                        "deletionBoundaryVersion", n("103"),
                        "pendingReset", AttributeValue.builder().nul(true).build()))
                .build());

        assertFalse(reader.findResetPoint().orElseThrow().resetInProgress());
    }

    @Test
    void readsHistoricalRevisionUsingPaddedRevisionKeyAndStrongRead() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("MEMORY_REVISION"),
                        "schemaVersion", n("1"),
                        "memoryId", s(ID.value()),
                        "revisionNumber", n("12"),
                        "afterVersion", n("12"),
                        "changeType", s("UPDATE"),
                        "afterSnapshot", m(Map.of(
                                "content", s("Javaを仕事で使っている。"),
                                "category", s("ENGINEERING"),
                                "captureType", s("EXPLICIT"),
                                "sensitivityLevel", s("NORMAL"),
                                "memoryState", s("ACTIVE"),
                                "updatedAt", s("2026-10-12T10:00:00+09:00")))))
                .build());

        var result = reader.findHistoricalRevision(ID, 12).orElseThrow();

        assertEquals(12, result.revisionNumber());
        assertEquals("Javaを仕事で使っている。", result.content());
        assertEquals("NORMAL", result.sensitivityLevel());
        var captor = org.mockito.ArgumentCaptor.forClass(GetItemRequest.class);
        verify(client).getItem(captor.capture());
        GetItemRequest request = captor.getValue();
        assertTrue(request.consistentRead());
        assertEquals("MEMORY#" + ID.value(), request.key().get("pk").s());
        assertEquals("REVISION#00000000000000000012", request.key().get("sk").s());
    }

    @Test
    void rejectsRevisionWhenAuthoritativeAfterSnapshotIsMissing() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of(
                        "itemType", s("MEMORY_REVISION"),
                        "schemaVersion", n("1"),
                        "memoryId", s(ID.value()),
                        "revisionNumber", n("12"),
                        "afterVersion", n("12"),
                        "changeType", s("UPDATE")))
                .build());

        assertThrows(IllegalStateException.class, () -> reader.findHistoricalRevision(ID, 12));
    }

    @Test
    void missingRevisionReturnsEmptyAndInvalidRevisionIsRejected() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertTrue(reader.findHistoricalRevision(ID, 2).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> reader.findHistoricalRevision(ID, 0));
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(String value) {
        return AttributeValue.builder().n(value).build();
    }

    private static AttributeValue b(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    private static AttributeValue m(Map<String, AttributeValue> value) {
        return AttributeValue.builder().m(value).build();
    }
}
