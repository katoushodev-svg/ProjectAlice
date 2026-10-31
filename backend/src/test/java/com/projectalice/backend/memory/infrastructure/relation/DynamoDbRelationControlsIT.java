package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class DynamoDbRelationControlsIT {
    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.0")
            .withExposedPorts(8000).waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    private static DynamoDbClient client;
    private static String table;
    private static DynamoDbRelationControlRepository controls;
    private static DynamoDbRelationNonceProvider nonces;
    private static DynamoDbRelationKeyReferenceStore references;

    @BeforeAll static void setUp() {
        DYNAMODB.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy"))).build();
        table = "alice-relation-control-" + UUID.randomUUID();
        client.createTable(CreateTableRequest.builder().tableName(table)
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()).build());
        client.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(table).build());
        Clock clock = Clock.fixed(OffsetDateTime.parse("2026-10-29T12:00:00+09:00").toInstant(), ZoneOffset.UTC);
        controls = new DynamoDbRelationControlRepository(client, table, clock);
        nonces = new DynamoDbRelationNonceProvider(client, table);
        references = new DynamoDbRelationKeyReferenceStore(client, table, clock);
    }

    @AfterAll static void tearDown() {
        if (client != null) {
            try { client.deleteTable(request -> request.tableName(table)); }
            catch (ResourceNotFoundException ignored) { }
            finally { client.close(); }
        }
        DYNAMODB.stop();
    }

    @BeforeEach void resetItems() {
        client.deleteItem(request -> request.tableName(table)
                .key(Map.of("pk", s(DynamoDbRelationControlRepository.GLOBAL_PK),
                        "sk", s(DynamoDbRelationControlRepository.CONTROL_SK))));
        client.deleteItem(request -> request.tableName(table)
                .key(Map.of("pk", s(DynamoDbRelationControlRepository.KEY_PK),
                        "sk", s(DynamoDbRelationControlRepository.CONTROL_SK))));
        client.deleteItem(request -> request.tableName(table).key(Map.of("pk",
                s(DynamoDbRelationControlRepository.KEY_PK + "#3"),
                "sk", s("REVIEW#8a1f8e2a-4b3c-4d5e-8f60-123456789abc"))));
    }

    @Test void initializesBothControlsAtomicallyAndAllocatesUniqueConcurrentNonces() throws Exception {
        assertTrue(controls.initializeAfterKeyProbe(keyProvider(3)));
        assertFalse(controls.initializeAfterKeyProbe(keyProvider(3)));
        var snapshot = controls.readControls();
        assertEquals(0, snapshot.global().activeCount());
        assertEquals(20, snapshot.global().maxActiveCount());
        assertEquals(2, snapshot.keys().maxActiveKeyVersions());
        assertEquals(RelationKeyControl.KeyState.WRITE_ACTIVE, snapshot.keys().versions().getFirst().state());

        Set<String> issued = ConcurrentHashMap.newKeySet();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 40).<Runnable>mapToObj(i -> () -> {
                byte[] nonce = nonces.reserveNonce(3);
                assertEquals(12, nonce.length);
                issued.add(HexFormat.of().formatHex(nonce));
            }).toList();
            tasks.forEach(executor::submit);
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(40, issued.size());
        assertEquals(40, controls.readControls().keys().versions().getFirst().nextNonceCounter());
    }

    @Test void preparingReferenceAndKeyCountsCommitTogetherAndRetriesAreIdempotent() {
        controls.initializeAfterKeyProbe(keyProvider(3));
        var id = new RelationKeyReference.Key(3, "8a1f8e2a-4b3c-4d5e-8f60-123456789abc");
        var reference = new RelationKeyReference(id, RelationKeyReference.State.PREPARING, 1,
                OffsetDateTime.parse("2026-10-29T12:00:00+09:00"));
        assertEquals(RelationKeyReferenceStore.CreateResult.CREATED, references.createPreparingIfAbsent(reference));
        assertEquals(RelationKeyReferenceStore.CreateResult.SAME_REFERENCE_EXISTS,
                references.createPreparingIfAbsent(reference));
        var conflict = new RelationKeyReference(id, RelationKeyReference.State.PREPARING, 2,
                reference.createdAt());
        assertEquals(RelationKeyReferenceStore.CreateResult.IDENTITY_CONFLICT,
                references.createPreparingIfAbsent(conflict));
        assertEquals(1, controls.readControls().keys().versions().getFirst().preparingReferenceCount());

        assertEquals(RelationKeyReferenceStore.TransitionResult.UPDATED,
                references.transition(id, 1, RelationKeyReference.State.PREPARING,
                        RelationKeyReference.State.CLEANUP_PENDING));
        assertEquals(RelationKeyReferenceStore.TransitionResult.ALREADY_AT_TARGET,
                references.transition(id, 1, RelationKeyReference.State.PREPARING,
                        RelationKeyReference.State.CLEANUP_PENDING));
        var after = controls.readControls().keys().versions().getFirst();
        assertEquals(0, after.preparingReferenceCount());
        assertEquals(1, after.cleanupPendingCount());
        assertEquals(RelationKeyReferenceStore.TransitionResult.REVIEW_LIFECYCLE_TRANSACTION_REQUIRED,
                references.transition(id, 1, RelationKeyReference.State.CLEANUP_PENDING,
                        RelationKeyReference.State.ACTIVE));
        assertEquals(RelationKeyReferenceStore.ReleaseResult.REVIEW_LIFECYCLE_TRANSACTION_REQUIRED,
                references.releaseAfterCleanup(id, 1));
    }

    @Test void failedConditionalTransactionDoesNotCreateReferenceOrIncrementCounts() {
        controls.initializeAfterKeyProbe(keyProvider(3));
        var id = new RelationKeyReference.Key(9, "8a1f8e2a-4b3c-4d5e-8f60-123456789abc");
        var reference = new RelationKeyReference(id, RelationKeyReference.State.PREPARING, 1,
                OffsetDateTime.parse("2026-10-29T12:00:00+09:00"));
        assertThrows(RelationControlUnavailableException.class, () -> references.createPreparingIfAbsent(reference));
        assertEquals(0, controls.readControls().keys().versions().getFirst().preparingReferenceCount());
    }

    private static software.amazon.awssdk.services.dynamodb.model.AttributeValue s(String value) {
        return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(value).build();
    }

    private static RelationStateKeyProvider keyProvider(int version) {
        var key = new javax.crypto.spec.SecretKeySpec(new byte[32], "AES");
        return new RelationStateKeyProvider() {
            @Override public void probeRelationPurpose() { }
            @Override public KeyMaterial activeEncryptionKey() { return new KeyMaterial(version, key); }
            @Override public javax.crypto.SecretKey decryptionKey(int requestedVersion) { return key; }
        };
    }
}
