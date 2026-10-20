package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.projectalice.backend.memory.application.port.out.MemoryPreferencesUpdateConflictException;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class DynamoDbMemoryPreferencesRepositoryIT {

    private static final String PREFERENCES_PK = "MEMORY_SETTINGS#GLOBAL";
    private static final String PREFERENCES_SK = "PREFERENCES";

    private static final String FEATURE_STATE_PK = "MEMORY_SETTINGS#GLOBAL";
    private static final String FEATURE_STATE_SK = "FEATURE_STATE";

    private static final String PREFERENCES_ITEM_TYPE = "MEMORY_PREFERENCES";
    private static final String FEATURE_STATE_ITEM_TYPE = "MEMORY_FEATURE_STATE";

    private static final OffsetDateTime INITIALIZED_AT =
            OffsetDateTime.parse("2026-10-16T10:00:00.000+09:00");

    private static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>("amazon/dynamodb-local:3.3.0")
                    .withExposedPorts(8000)
                    .waitingFor(
                            Wait.forListeningPort()
                                    .withStartupTimeout(Duration.ofSeconds(30)));

    private static DynamoDbClient client;
    private static String tableName;
    private static DynamoDbMemoryPreferencesRepository repository;

    @BeforeAll
    static void setUp() {
        DYNAMODB.start();

        client = DynamoDbClient.builder()
                .endpointOverride(
                        URI.create(
                                "http://"
                                        + DYNAMODB.getHost()
                                        + ":"
                                        + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        tableName = "alice-preferences-test-" + UUID.randomUUID();

        client.createTable(
                CreateTableRequest.builder()
                        .tableName(tableName)
                        .keySchema(
                                KeySchemaElement.builder()
                                        .attributeName("pk")
                                        .keyType(KeyType.HASH)
                                        .build(),
                                KeySchemaElement.builder()
                                        .attributeName("sk")
                                        .keyType(KeyType.RANGE)
                                        .build())
                        .attributeDefinitions(
                                AttributeDefinition.builder()
                                        .attributeName("pk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build(),
                                AttributeDefinition.builder()
                                        .attributeName("sk")
                                        .attributeType(ScalarAttributeType.S)
                                        .build())
                        .provisionedThroughput(
                                ProvisionedThroughput.builder()
                                        .readCapacityUnits(5L)
                                        .writeCapacityUnits(5L)
                                        .build())
                        .build());

        client.waiter()
                .waitUntilTableExists(
                        DescribeTableRequest.builder()
                                .tableName(tableName)
                                .build());

        repository = new DynamoDbMemoryPreferencesRepository(client, tableName);
    }

    @BeforeEach
    void prepareFeatureState() {
        // 各テストの設定データを独立させる。
        client.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(key(PREFERENCES_PK, PREFERENCES_SK))
                        .build());

        client.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(key(FEATURE_STATE_PK, FEATURE_STATE_SK))
                        .build());

        // 初期化処理が必要な状態を作成する。
        client.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(
                                java.util.Map.of(
                                        "pk", stringValue(FEATURE_STATE_PK),
                                        "sk", stringValue(FEATURE_STATE_SK),
                                        "itemType", stringValue(FEATURE_STATE_ITEM_TYPE),
                                        "schemaVersion", numberValue(1),
                                        "preferencesInitialized",
                                                boolValue(false),
                                        "createdAt",
                                                stringValue(
                                                        INITIALIZED_AT.toString())))
                        .build());
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            try {
                client.deleteTable(
                        request -> request.tableName(tableName));
            } catch (ResourceNotFoundException ignored) {
                // テスト後のクリーンアップ。
            } finally {
                client.close();
            }
        }

        DYNAMODB.stop();
    }

    @Test
    void getOrInitializeCreatesDefaultPreferences() {
        MemoryPreferences preferences =
                repository.getOrInitialize(INITIALIZED_AT);

        assertTrue(preferences.autoSaveEnabled());
        assertTrue(preferences.answerUseEnabled());
        assertEquals(1L, preferences.version());
        assertEquals(
                INITIALIZED_AT,
                preferences.updatedAt());

        assertEquals(
                "MEMORY_PREFERENCES",
                readItemType(PREFERENCES_PK, PREFERENCES_SK));

        assertEquals(
                "true",
                readBooleanAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "autoSaveEnabled"));

        assertEquals(
                "true",
                readBooleanAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "answerUseEnabled"));

        assertEquals(
                "true",
                readBooleanAsString(
                        FEATURE_STATE_PK,
                        FEATURE_STATE_SK,
                        "preferencesInitialized"));
    }

    @Test
    void getOrInitializeReturnsExistingPreferencesWithoutResettingSettings() {
        repository.getOrInitialize(INITIALIZED_AT);

        OffsetDateTime changedAt =
                OffsetDateTime.parse("2026-10-16T10:05:00.000+09:00");

        MemoryPreferences updated =
                repository.update(
                        false,
                        true,
                        1L,
                        changedAt);

        assertEquals(2L, updated.version());
        assertTrue(!updated.autoSaveEnabled());
        assertTrue(updated.answerUseEnabled());

        MemoryPreferences loaded =
                repository.getOrInitialize(
                        OffsetDateTime.parse(
                                "2026-10-16T10:10:00.000+09:00"));

        assertTrue(!loaded.autoSaveEnabled());
        assertTrue(loaded.answerUseEnabled());
        assertEquals(2L, loaded.version());

        // 既存設定を読み込んだだけでは updatedAt を更新しない。
        assertEquals(changedAt, loaded.updatedAt());
    }

    @Test
    void updateChangesSettingsAndIncrementsVersionOnce() {
        repository.getOrInitialize(INITIALIZED_AT);

        OffsetDateTime changedAt =
                OffsetDateTime.parse("2026-10-16T10:05:00.000+09:00");

        MemoryPreferences updated =
                repository.update(
                        false,
                        true,
                        1L,
                        changedAt);

        assertTrue(!updated.autoSaveEnabled());
        assertTrue(updated.answerUseEnabled());
        assertEquals(2L, updated.version());
        assertEquals(changedAt, updated.updatedAt());

        assertEquals(
                "2",
                readNumberAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "version"));
    }

    @Test
    void updateWithNoChangesDoesNotIncrementVersionOrUpdateTimestamp() {
        repository.getOrInitialize(INITIALIZED_AT);

        MemoryPreferences unchanged =
                repository.update(
                        true,
                        true,
                        1L,
                        OffsetDateTime.parse(
                                "2026-10-16T10:05:00.000+09:00"));

        assertTrue(unchanged.autoSaveEnabled());
        assertTrue(unchanged.answerUseEnabled());
        assertEquals(1L, unchanged.version());
        assertEquals(INITIALIZED_AT, unchanged.updatedAt());

        assertEquals(
                "1",
                readNumberAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "version"));

        assertEquals(
                "2026-10-16T10:00:00.000+09:00",
                readString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "updatedAt"));
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        repository.getOrInitialize(INITIALIZED_AT);

        repository.update(
                false,
                true,
                1L,
                OffsetDateTime.parse(
                        "2026-10-16T10:05:00.000+09:00"));

        assertThrows(
                MemoryPreferencesUpdateConflictException.class,
                () ->
                        repository.update(
                                true,
                                false,
                                1L,
                                OffsetDateTime.parse(
                                        "2026-10-16T10:10:00.000+09:00")));

        // 競合した更新によってデータが上書きされていないことを確認する。
        assertEquals(
                "2",
                readNumberAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "version"));

        assertEquals(
                "false",
                readBooleanAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "autoSaveEnabled"));

        assertEquals(
                "true",
                readBooleanAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "answerUseEnabled"));
    }

    @Test
    void getOrInitializeFailsWhenFeatureStateIsMissing() {
        client.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(key(FEATURE_STATE_PK, FEATURE_STATE_SK))
                        .build());

        assertThrows(
                IllegalStateException.class,
                () -> repository.getOrInitialize(INITIALIZED_AT));
    }

    @Test
    void oversizedFeatureStateFailsBeforePreferencesAreCreated() {
        client.updateItem(request -> request
                .tableName(tableName)
                .key(key(FEATURE_STATE_PK, FEATURE_STATE_SK))
                .updateExpression("SET #extra = :extra")
                .expressionAttributeNames(java.util.Map.of("#extra", "oversizedExtra"))
                .expressionAttributeValues(java.util.Map.of(
                        ":extra", stringValue("x".repeat(5000)))));

        assertThrows(
                IllegalStateException.class,
                () -> repository.getOrInitialize(INITIALIZED_AT));

        assertTrue(client.getItem(request -> request
                .tableName(tableName)
                .key(key(PREFERENCES_PK, PREFERENCES_SK))
                .consistentRead(true))
                .item()
                .isEmpty());
    }

    @Test
    void oversizedPreferencesFailsBeforeUpdateIsSent() {
        repository.getOrInitialize(INITIALIZED_AT);

        client.updateItem(request -> request
                .tableName(tableName)
                .key(key(PREFERENCES_PK, PREFERENCES_SK))
                .updateExpression("SET #extra = :extra")
                .expressionAttributeNames(java.util.Map.of("#extra", "oversizedExtra"))
                .expressionAttributeValues(java.util.Map.of(
                        ":extra", stringValue("x".repeat(5000)))));

        assertThrows(
                IllegalStateException.class,
                () -> repository.update(
                        false,
                        true,
                        1L,
                        OffsetDateTime.parse("2026-10-16T10:05:00.000+09:00")));

        assertEquals(
                "1",
                readNumberAsString(
                        PREFERENCES_PK,
                        PREFERENCES_SK,
                        "version"));
        assertTrue(readBooleanAsString(
                PREFERENCES_PK,
                PREFERENCES_SK,
                "autoSaveEnabled").equals("true"));
    }

    private static java.util.Map<String, AttributeValue> key(
            String pk,
            String sk) {
        return java.util.Map.of(
                "pk", stringValue(pk),
                "sk", stringValue(sk));
    }

    private static AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue numberValue(long value) {
        return AttributeValue.builder()
                .n(Long.toString(value))
                .build();
    }

    private static AttributeValue boolValue(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    private static String readItemType(String pk, String sk) {
        return readString(pk, sk, "itemType");
    }

    private static String readString(
            String pk,
            String sk,
            String attributeName) {
        AttributeValue value =
                client.getItem(
                                request -> request
                                        .tableName(tableName)
                                        .key(key(pk, sk))
                                        .consistentRead(true))
                        .item()
                        .get(attributeName);

        return value == null ? null : value.s();
    }

    private static String readNumberAsString(
            String pk,
            String sk,
            String attributeName) {
        AttributeValue value =
                client.getItem(
                                request -> request
                                        .tableName(tableName)
                                        .key(key(pk, sk))
                                        .consistentRead(true))
                        .item()
                        .get(attributeName);

        return value == null ? null : value.n();
    }

    private static String readBooleanAsString(
            String pk,
            String sk,
            String attributeName) {
        AttributeValue value =
                client.getItem(
                                request -> request
                                        .tableName(tableName)
                                        .key(key(pk, sk))
                                        .consistentRead(true))
                        .item()
                        .get(attributeName);

        return value == null || value.bool() == null
                ? null
                : value.bool().toString();
    }
}