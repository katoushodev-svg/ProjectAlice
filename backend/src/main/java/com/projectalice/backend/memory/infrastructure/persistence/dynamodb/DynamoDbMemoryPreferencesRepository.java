package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.application.port.out.MemoryPreferencesRepository;
import com.projectalice.backend.memory.application.port.out.MemoryPreferencesUpdateConflictException;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

public final class DynamoDbMemoryPreferencesRepository
        implements MemoryPreferencesRepository {

    private static final String GLOBAL_PK = "MEMORY_SETTINGS#GLOBAL";

    private static final String PREFERENCES_SK = "PREFERENCES";

    private static final String FEATURE_STATE_SK = "FEATURE_STATE";

    private static final String PREFERENCES_ITEM_TYPE = "MEMORY_PREFERENCES";

    private static final String FEATURE_STATE_ITEM_TYPE = "MEMORY_FEATURE_STATE";

    private static final long SCHEMA_VERSION = 1L;

    private static final int MAX_ITEM_SIZE_BYTES = 4 * 1024;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    private final DynamoDbClient client;

    private final String tableName;

    public DynamoDbMemoryPreferencesRepository(
            DynamoDbClient client,
            String tableName) {

        this.client = Objects.requireNonNull(
                client, "client must not be null");

        this.tableName = Objects.requireNonNull(
                tableName, "tableName must not be null");

        if (tableName.isBlank()) {
            throw new IllegalArgumentException(
                    "tableName must not be blank");
        }
    }

    @Override
    public MemoryPreferences getOrInitialize(
            OffsetDateTime initializedAt) {

        Objects.requireNonNull(
                initializedAt, "initializedAt must not be null");

        try {
            return getOrInitializeInternal(initializedAt);
        } catch (TransactionCanceledException exception) {
            return convergeAfterInitializationConflict(
                    initializedAt, exception);
        } catch (RuntimeException exception) {
            throw persistenceFailure(exception);
        }
    }

    private MemoryPreferences getOrInitializeInternal(
            OffsetDateTime initializedAt) {

        Map<String, AttributeValue> featureState =
                readFeatureState();

        boolean initialized = readPreferencesInitialized(featureState);

        Map<String, AttributeValue> preferences =
                readPreferencesItem();

        if (initialized) {
            if (preferences.isEmpty()) {
                throw new IllegalStateException(
                        "Memory Preferences are missing although Feature State "
                                + "indicates initialization is complete");
            }

            return toDomain(preferences);
        }

        if (!preferences.isEmpty()) {
            throw new IllegalStateException(
                    "Memory Preferences exist while Feature State indicates "
                            + "initialization is incomplete");
        }

        initializePreferences(initializedAt, featureState);

        Map<String, AttributeValue> initializedPreferences =
                readPreferencesItem();

        if (initializedPreferences.isEmpty()) {
            throw new IllegalStateException(
                    "Memory Preferences initialization did not produce an item");
        }

        Map<String, AttributeValue> initializedFeatureState =
                readFeatureState();

        if (!readPreferencesInitialized(initializedFeatureState)) {
            throw new IllegalStateException(
                    "Memory Preferences initialization was not finalized");
        }

        return toDomain(initializedPreferences);
    }

    private void initializePreferences(
            OffsetDateTime initializedAt,
            Map<String, AttributeValue> featureState) {

        MemoryPreferences defaults =
                MemoryPreferences.create(initializedAt);

        Map<String, AttributeValue> preferencesItem = toItem(defaults);
        validateItemSize(preferencesItem, "Memory Preferences");

        Map<String, AttributeValue> projectedFeatureState =
                new HashMap<>(featureState);
        projectedFeatureState.put("preferencesInitialized", bool(true));
        validateItemSize(projectedFeatureState, "Memory Feature State");

        Put preferencesPut = Put.builder()
                .tableName(tableName)
                .item(preferencesItem)
                .conditionExpression(
                        "attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
                .expressionAttributeNames(Map.of(
                        "#pk", "pk",
                        "#sk", "sk"))
                .build();

        Update featureStateUpdate = Update.builder()
                .tableName(tableName)
                .key(featureStateKey())
                .updateExpression(
                        "SET #preferencesInitialized = :initialized")
                .conditionExpression(
                        "#itemType = :itemType "
                                + "AND #schemaVersion = :schemaVersion "
                                + "AND #preferencesInitialized = :notInitialized")
                .expressionAttributeNames(Map.of(
                        "#itemType", "itemType",
                        "#schemaVersion", "schemaVersion",
                        "#preferencesInitialized", "preferencesInitialized"))
                .expressionAttributeValues(Map.of(
                        ":initialized", bool(true),
                        ":itemType", s(FEATURE_STATE_ITEM_TYPE),
                        ":schemaVersion", n(SCHEMA_VERSION),
                        ":notInitialized", bool(false)))
                .build();

        client.transactWriteItems(
                TransactWriteItemsRequest.builder()
                        .transactItems(List.of(
                                TransactWriteItem.builder()
                                        .put(preferencesPut)
                                        .build(),
                                TransactWriteItem.builder()
                                        .update(featureStateUpdate)
                                        .build()))
                        .build());
    }

    private MemoryPreferences convergeAfterInitializationConflict(
            OffsetDateTime initializedAt,
            TransactionCanceledException originalException) {

        try {
            Map<String, AttributeValue> featureState =
                    readFeatureState();

            Map<String, AttributeValue> preferences =
                    readPreferencesItem();

            if (readPreferencesInitialized(featureState)
                    && !preferences.isEmpty()) {
                return toDomain(preferences);
            }
        } catch (RuntimeException verificationFailure) {
            originalException.addSuppressed(verificationFailure);
        }

        throw new IllegalStateException(
                "Memory Preferences initialization could not be verified",
                originalException);
    }

    @Override
    public MemoryPreferences update(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long expectedVersion,
            OffsetDateTime changedAt) {

        Objects.requireNonNull(
                changedAt, "changedAt must not be null");

        if (expectedVersion < 1) {
            throw new IllegalArgumentException(
                    "expectedVersion must be >= 1");
        }

        try {
            return updateInternal(
                    autoSaveEnabled,
                    answerUseEnabled,
                    expectedVersion,
                    changedAt);
        } catch (MemoryPreferencesUpdateConflictException exception) {
            throw exception;
        } catch (ConditionalCheckFailedException exception) {
            throw classifyUpdateConflict(expectedVersion, exception);
        } catch (RuntimeException exception) {
            throw persistenceFailure(exception);
        }
    }

    private MemoryPreferences updateInternal(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long expectedVersion,
            OffsetDateTime changedAt) {

        /*
         * Validate the authoritative Feature State and ensure that the
         * Preferences item is initialized before attempting an update.
         */
        MemoryPreferences current = getOrInitialize(changedAt);

        if (current.version() != expectedVersion) {
            throw new MemoryPreferencesUpdateConflictException(
                    "Expected preferences version " + expectedVersion
                            + " but current version is " + current.version());
        }

        /*
         * A no-op must not write to DynamoDB or change the version/timestamp.
         */
        if (current.autoSaveEnabled() == autoSaveEnabled
                && current.answerUseEnabled() == answerUseEnabled) {
            return current;
        }

        MemoryPreferences changed = current.change(
                autoSaveEnabled,
                answerUseEnabled,
                changedAt);

        Map<String, AttributeValue> values = new HashMap<>();

        values.put(":itemType", s(PREFERENCES_ITEM_TYPE));
        values.put(":expectedVersion", n(expectedVersion));
        values.put(":longMax", n(Long.MAX_VALUE));
        values.put(":autoSaveEnabled", bool(changed.autoSaveEnabled()));
        values.put(":answerUseEnabled", bool(changed.answerUseEnabled()));
        values.put(":nextVersion", n(changed.version()));
        values.put(":updatedAt", s(formatTimestamp(changed.updatedAt())));

        Map<String, AttributeValue> currentItem = readPreferencesItem();
        if (currentItem.isEmpty()) {
            throw new IllegalStateException(
                    "Memory Preferences are missing before update");
        }
        MemoryPreferences verifiedCurrent = toDomain(currentItem);
        if (verifiedCurrent.version() != expectedVersion) {
            throw new MemoryPreferencesUpdateConflictException(
                    "Expected preferences version " + expectedVersion
                            + " but current version is "
                            + verifiedCurrent.version());
        }

        Map<String, AttributeValue> projectedPreferences =
                new HashMap<>(currentItem);
        projectedPreferences.put("autoSaveEnabled", bool(changed.autoSaveEnabled()));
        projectedPreferences.put("answerUseEnabled", bool(changed.answerUseEnabled()));
        projectedPreferences.put("version", n(changed.version()));
        projectedPreferences.put("updatedAt", s(formatTimestamp(changed.updatedAt())));
        validateItemSize(projectedPreferences, "Memory Preferences");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(preferencesKey())
                .updateExpression(
                        "SET #autoSaveEnabled = :autoSaveEnabled, "
                                + "#answerUseEnabled = :answerUseEnabled, "
                                + "#version = :nextVersion, "
                                + "#updatedAt = :updatedAt")
                .conditionExpression(
                        "#itemType = :itemType "
                                + "AND #version = :expectedVersion "
                                + "AND #version < :longMax")
                .expressionAttributeNames(Map.of(
                        "#itemType", "itemType",
                        "#autoSaveEnabled", "autoSaveEnabled",
                        "#answerUseEnabled", "answerUseEnabled",
                        "#version", "version",
                        "#updatedAt", "updatedAt"))
                .expressionAttributeValues(Map.copyOf(values))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        Map<String, AttributeValue> updated =
                client.updateItem(request).attributes();

        if (updated == null || updated.isEmpty()) {
            throw new IllegalStateException(
                    "DynamoDB did not return the updated Memory Preferences");
        }

        return toDomain(updated);
    }

    private MemoryPreferencesUpdateConflictException classifyUpdateConflict(
            long expectedVersion,
            ConditionalCheckFailedException originalException) {

        final Map<String, AttributeValue> currentItem;

        try {
            currentItem = readPreferencesItem();
        } catch (RuntimeException verificationFailure) {
            originalException.addSuppressed(verificationFailure);
            throw new IllegalStateException(
                    "Memory Preferences update could not be verified",
                    originalException);
        }

        if (currentItem.isEmpty()) {
            throw new IllegalStateException(
                    "Memory Preferences disappeared during update",
                    originalException);
        }

        final MemoryPreferences current;

        try {
            current = toDomain(currentItem);
        } catch (RuntimeException verificationFailure) {
            originalException.addSuppressed(verificationFailure);
            throw new IllegalStateException(
                    "Memory Preferences update could not be verified",
                    originalException);
        }

        if (current.version() != expectedVersion) {
            return new MemoryPreferencesUpdateConflictException(
                    "Expected preferences version " + expectedVersion
                            + " but current version is " + current.version(),
                    originalException);
        }

        /*
         * A failed conditional update with the expected version still present
         * is not a confirmed version conflict. Fail closed instead of returning
         * a misleading HTTP 412.
         */
        throw new IllegalStateException(
                "Memory Preferences update condition failed without a version conflict",
                originalException);
    }

    private Map<String, AttributeValue> readFeatureState() {
        Map<String, AttributeValue> item = client.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(featureStateKey())
                        .consistentRead(true)
                        .build())
                .item();

        if (item == null || item.isEmpty()) {
            throw new IllegalStateException(
                    "Memory Feature State is missing");
        }

        if (!FEATURE_STATE_ITEM_TYPE.equals(
                requiredString(item, "itemType"))) {
            throw new IllegalStateException(
                    "Memory Feature State has an unsupported itemType");
        }

        if (requiredLong(item, "schemaVersion") != SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Memory Feature State has an unsupported schemaVersion");
        }

        return item;
    }

    private boolean readPreferencesInitialized(
            Map<String, AttributeValue> featureState) {

        AttributeValue value =
                featureState.get("preferencesInitialized");

        if (value == null || value.bool() == null) {
            throw new IllegalStateException(
                    "Memory Feature State has an invalid preferencesInitialized");
        }

        return value.bool();
    }

    private Map<String, AttributeValue> readPreferencesItem() {
        Map<String, AttributeValue> item = client.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(preferencesKey())
                        .consistentRead(true)
                        .build())
                .item();

        return item == null ? Map.of() : item;
    }

    private static MemoryPreferences toDomain(
            Map<String, AttributeValue> item) {

        if (!PREFERENCES_ITEM_TYPE.equals(
                requiredString(item, "itemType"))) {
            throw new IllegalStateException(
                    "Memory Preferences has an unsupported itemType");
        }

        if (requiredLong(item, "schemaVersion") != SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Memory Preferences has an unsupported schemaVersion");
        }

        boolean autoSaveEnabled =
                requiredBoolean(item, "autoSaveEnabled");

        boolean answerUseEnabled =
                requiredBoolean(item, "answerUseEnabled");

        long version = requiredLong(item, "version");

        OffsetDateTime updatedAt;

        try {
            updatedAt = OffsetDateTime.parse(
                    requiredString(item, "updatedAt"));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Memory Preferences has an invalid updatedAt",
                    exception);
        }

        return MemoryPreferences.reconstitute(
                autoSaveEnabled,
                answerUseEnabled,
                version,
                updatedAt);
    }

    private static Map<String, AttributeValue> toItem(
            MemoryPreferences preferences) {

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("pk", s(GLOBAL_PK));
        item.put("sk", s(PREFERENCES_SK));
        item.put("itemType", s(PREFERENCES_ITEM_TYPE));
        item.put("schemaVersion", n(SCHEMA_VERSION));
        item.put("autoSaveEnabled", bool(preferences.autoSaveEnabled()));
        item.put("answerUseEnabled", bool(preferences.answerUseEnabled()));
        item.put("version", n(preferences.version()));
        item.put("updatedAt", s(formatTimestamp(preferences.updatedAt())));

        return Map.copyOf(item);
    }

    private static Map<String, AttributeValue> preferencesKey() {
        return Map.of(
                "pk", s(GLOBAL_PK),
                "sk", s(PREFERENCES_SK));
    }

    private static Map<String, AttributeValue> featureStateKey() {
        return Map.of(
                "pk", s(GLOBAL_PK),
                "sk", s(FEATURE_STATE_SK));
    }

    private static String requiredString(
            Map<String, AttributeValue> item,
            String name) {

        AttributeValue value = item.get(name);

        if (value == null || value.s() == null) {
            throw new IllegalStateException(
                    "Memory Preferences configuration is missing or has an invalid "
                            + name);
        }

        return value.s();
    }

    private static long requiredLong(
            Map<String, AttributeValue> item,
            String name) {

        AttributeValue value = item.get(name);

        if (value == null || value.n() == null) {
            throw new IllegalStateException(
                    "Memory Preferences configuration is missing or has an invalid "
                            + name);
        }

        try {
            return Long.parseLong(value.n());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Memory Preferences configuration has an invalid " + name,
                    exception);
        }
    }

    private static boolean requiredBoolean(
            Map<String, AttributeValue> item,
            String name) {

        AttributeValue value = item.get(name);

        if (value == null || value.bool() == null) {
            throw new IllegalStateException(
                    "Memory Preferences configuration is missing or has an invalid "
                            + name);
        }

        return value.bool();
    }

    private static String formatTimestamp(OffsetDateTime value) {
        return TIMESTAMP_FORMAT.format(value);
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder()
                .n(Long.toString(value))
                .build();
    }

    private static AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    /**
     * Conservatively estimates DynamoDB item size before a write.
     *
     * Attribute names and scalar values are counted in UTF-8 bytes. One
     * additional byte per value is included as conservative overhead.
     * Unsupported or nested attribute types fail closed.
     */
    private static void validateItemSize(
            Map<String, AttributeValue> item,
            String itemName) {

        long size = 0;

        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            size += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
            AttributeValue value = entry.getValue();

            if (value == null) {
                throw new IllegalStateException(
                        itemName + " contains a null attribute value");
            }

            if (value.s() != null) {
                size += value.s().getBytes(StandardCharsets.UTF_8).length + 1L;
            } else if (value.n() != null) {
                size += value.n().getBytes(StandardCharsets.UTF_8).length + 1L;
            } else if (value.bool() != null) {
                size += 2L;
            } else if (value.b() != null) {
                size += value.b().asByteArray().length + 1L;
            } else {
                throw new IllegalStateException(
                        itemName + " contains an unsupported attribute type");
            }

            if (size > MAX_ITEM_SIZE_BYTES) {
                throw new IllegalStateException(
                        itemName + " exceeds the 4 KiB item size limit");
            }
        }
    }

    private static IllegalStateException persistenceFailure(
            RuntimeException exception) {

        if (exception instanceof IllegalStateException stateException) {
            return stateException;
        }

        return new IllegalStateException(
                "Memory Preferences persistence operation failed",
                exception);
    }
}