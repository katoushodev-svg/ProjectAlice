package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.domain.MemoryId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Strongly-consistent reads for the authoritative evidence used by final search validation.
 *
 * <p>Physical keys and attribute names follow Database Design sections 34.4, 36.5 and 37.6.
 * This reader deliberately returns evidence snapshots rather than deciding eligibility.</p>
 */
public final class DynamoDbSearchEvidenceReader {

    public static final String PROJECTION_PK_PREFIX = "MEMORY_SEARCH#PROJECTION#";
    public static final String RESET_POINT_PK = "MEMORY_SETTINGS#PRIMARY";
    public static final String RESET_POINT_SK = "RESET_POINT";

    private static final String META_SK = "META";
    private static final String PERSONAL_MEMORY_PK_PREFIX = "MEMORY#";
    private static final String REVISION_SK_PREFIX = "REVISION#";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbSearchEvidenceReader(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = Objects.requireNonNull(dynamoDbClient, "dynamoDbClient must not be null");
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        this.tableName = tableName;
    }

    public Optional<CurrentProjectionSnapshot> findCurrentProjection(MemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Map<String, AttributeValue> item = getItem(
                PROJECTION_PK_PREFIX + memoryId.value(), META_SK);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        requireItemType(item, "MEMORY_SEARCH_PROJECTION", "Current Projection Metadata");
        requireNumber(item, "schemaVersion");
        requireString(item, "memoryId");
        if (!memoryId.value().equals(requireString(item, "memoryId"))) {
            throw malformed("Projection memoryId does not match its key");
        }

        long activeSemanticVersion = requireLong(item, "activeSemanticVersion");
        long tokenKeyVersion = requireLong(item, "tokenKeyVersion");
        String analyzerVersion = requireString(item, "analyzerVersion");
        String status = requireString(item, "status");
        boolean fallbackRequired = optionalBoolean(item, "fallbackRequired", false);
        if (activeSemanticVersion < 1 || tokenKeyVersion < 1) {
            throw malformed("Projection version fields must be positive");
        }
        return Optional.of(new CurrentProjectionSnapshot(
                memoryId, activeSemanticVersion, analyzerVersion, tokenKeyVersion, status, fallbackRequired));
    }

    public Optional<ResetPointSnapshot> findResetPoint() {
        Map<String, AttributeValue> item = getItem(RESET_POINT_PK, RESET_POINT_SK);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        requireItemType(item, "MEMORY_RESET_POINT", "Reset Point");
        requireNumber(item, "schemaVersion");
        long generation = requireLong(item, "generation");
        long deletionBoundaryVersion = requireLong(item, "deletionBoundaryVersion");
        if (generation < 0 || deletionBoundaryVersion < 0) {
            throw malformed("Reset Point generation/boundary must be non-negative");
        }

        boolean pendingReset = isPendingReset(item.get("pendingReset"));
        return Optional.of(new ResetPointSnapshot(generation, deletionBoundaryVersion, pendingReset));
    }

    public Optional<HistoricalRevisionSnapshot> findHistoricalRevision(
            MemoryId memoryId, long revisionNumber) {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be >= 1");
        }
        String revisionKey = REVISION_SK_PREFIX + "%020d".formatted(revisionNumber);
        Map<String, AttributeValue> item = getItem(
                PERSONAL_MEMORY_PK_PREFIX + memoryId.value(), revisionKey);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        requireItemType(item, "MEMORY_REVISION", "Memory Revision");
        requireNumber(item, "schemaVersion");
        String storedMemoryId = requireString(item, "memoryId");
        long storedRevisionNumber = requireLong(item, "revisionNumber");
        long afterVersion = requireLong(item, "afterVersion");
        String changeType = requireString(item, "changeType");
        if (!memoryId.value().equals(storedMemoryId)
                || storedRevisionNumber != revisionNumber
                || afterVersion != revisionNumber) {
            throw malformed("Revision identity/version does not match requested key");
        }
        AttributeValue afterSnapshotValue = item.get("afterSnapshot");
        if (afterSnapshotValue == null || afterSnapshotValue.m() == null) {
            throw malformed("Missing or invalid afterSnapshot");
        }
        Map<String, AttributeValue> snapshot = afterSnapshotValue.m();
        String content = requireString(snapshot, "content");
        String category = requireString(snapshot, "category");
        String captureType = requireString(snapshot, "captureType");
        String sensitivityLevel = requireString(snapshot, "sensitivityLevel");
        String memoryState = requireString(snapshot, "memoryState");
        String updatedAt = requireString(snapshot, "updatedAt");
        String confirmedAt = optionalString(snapshot, "confirmedAt");
        return Optional.of(new HistoricalRevisionSnapshot(
                memoryId, revisionNumber, changeType, content, category, captureType,
                sensitivityLevel, memoryState, updatedAt, confirmedAt));
    }

    private Map<String, AttributeValue> getItem(String pk, String sk) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s(pk).build(),
                        "sk", AttributeValue.builder().s(sk).build()))
                .consistentRead(true)
                .build());
        return response.item() == null ? Map.of() : response.item();
    }

    private static void requireItemType(
            Map<String, AttributeValue> item, String expected, String label) {
        String actual = requireString(item, "itemType");
        if (!expected.equals(actual)) {
            throw malformed(label + " itemType mismatch");
        }
    }

    private static String requireString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null || value.s().isBlank()) {
            throw malformed("Missing or invalid " + name);
        }
        return value.s();
    }

    private static void requireNumber(Map<String, AttributeValue> item, String name) {
        requireLong(item, name);
    }

    private static long requireLong(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) {
            throw malformed("Missing or invalid " + name);
        }
        try {
            return Long.parseLong(value.n());
        } catch (NumberFormatException exception) {
            throw malformed("Invalid numeric " + name);
        }
    }

    private static String optionalString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null) {
            return null;
        }
        if (value.s() == null || value.s().isBlank()) {
            throw malformed("Invalid optional string " + name);
        }
        return value.s();
    }

    private static boolean optionalBoolean(
            Map<String, AttributeValue> item, String name, boolean defaultValue) {
        AttributeValue value = item.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value.bool() == null) {
            throw malformed("Invalid boolean " + name);
        }
        return value.bool();
    }

    private static boolean isPendingReset(AttributeValue value) {
        if (value == null || Boolean.TRUE.equals(value.nul())) {
            return false;
        }
        // A pendingReset payload is an active reset marker. Fail closed on every
        // non-null representation; do not parse operation details into search logs.
        return true;
    }

    private static IllegalStateException malformed(String message) {
        return new IllegalStateException("Malformed DynamoDB search evidence: " + message);
    }

    public record CurrentProjectionSnapshot(
            MemoryId memoryId,
            long activeSemanticVersion,
            String analyzerVersion,
            long tokenKeyVersion,
            String status,
            boolean fallbackRequired) {
        public CurrentProjectionSnapshot {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            if (activeSemanticVersion < 1 || tokenKeyVersion < 1) {
                throw new IllegalArgumentException("projection versions must be positive");
            }
            if (analyzerVersion == null || analyzerVersion.isBlank()
                    || status == null || status.isBlank()) {
                throw new IllegalArgumentException("analyzerVersion/status must not be blank");
            }
        }

        public boolean readyFor(String expectedAnalyzerVersion) {
            return "READY".equals(status) && analyzerVersion.equals(expectedAnalyzerVersion);
        }
    }

    public record ResetPointSnapshot(
            long generation,
            long deletionBoundaryVersion,
            boolean resetInProgress) {
        public ResetPointSnapshot {
            if (generation < 0 || deletionBoundaryVersion < 0) {
                throw new IllegalArgumentException("generation/boundary must be non-negative");
            }
        }
    }

    /** Authoritative content-bearing afterSnapshot from a Memory Revision. */
    public record HistoricalRevisionSnapshot(
            MemoryId memoryId,
            long revisionNumber,
            String changeType,
            String content,
            String category,
            String captureType,
            String sensitivityLevel,
            String memoryState,
            String updatedAt,
            String confirmedAt) {
        public HistoricalRevisionSnapshot {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            if (revisionNumber < 1) {
                throw new IllegalArgumentException("revisionNumber must be >= 1");
            }
            requireNonBlank(changeType, "changeType");
            requireNonBlank(content, "content");
            requireNonBlank(category, "category");
            requireNonBlank(captureType, "captureType");
            requireNonBlank(sensitivityLevel, "sensitivityLevel");
            requireNonBlank(memoryState, "memoryState");
            requireNonBlank(updatedAt, "updatedAt");
            if (confirmedAt != null && confirmedAt.isBlank()) {
                throw new IllegalArgumentException("confirmedAt must be null or non-blank");
            }
        }

        private static void requireNonBlank(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
