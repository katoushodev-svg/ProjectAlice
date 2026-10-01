package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.port.out.MemoryUpdateConflictException;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class DynamoDbPersonalMemoryRepositoryIT {

    private static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>("amazon/dynamodb-local:3.3.0")
                    .withExposedPorts(8000)
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

    private static DynamoDbClient client;
    private static String tableName;
    private static DynamoDbPersonalMemoryRepository repository;

    @BeforeAll
    static void setUp() {
        DYNAMODB.start();

        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(
                        "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        tableName = "alice-test-" + UUID.randomUUID();
        client.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build());

        client.waiter().waitUntilTableExists(
                DescribeTableRequest.builder().tableName(tableName).build());

        repository = new DynamoDbPersonalMemoryRepository(client, tableName);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            try {
                client.deleteTable(request -> request.tableName(tableName));
            } catch (ResourceNotFoundException ignored) {
                // Test cleanup only.
            } finally {
                client.close();
            }
        }
        DYNAMODB.stop();
    }

    @Test
    void saveCreatesMemoryAndInitialRevisionAtomically() {
        PersonalMemory memory = sampleMemory();

        repository.save(memory);

        Map<String, AttributeValue> memoryItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("META").build())))
                .item();

        assertNotNull(memoryItem);
        assertEquals("PERSONAL_MEMORY", memoryItem.get("itemType").s());
        assertEquals("1", memoryItem.get("version").n());
        assertEquals(memory.content().value(), memoryItem.get("content").s());

        Map<String, AttributeValue> revisionItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("REVISION#00000000000000000001").build())))
                .item();

        assertNotNull(revisionItem);
        assertEquals("MEMORY_REVISION", revisionItem.get("itemType").s());
        assertEquals("CREATE", revisionItem.get("changeType").s());
        assertEquals("1", revisionItem.get("afterVersion").n());
        assertEquals(4, revisionItem.get("changedFields").l().size());
    }

        @Test
        void findByIdRestoresSavedMemoryFields() {
                PersonalMemory memory = sampleMemory();
                OffsetDateTime confirmedAt = memory.createdAt().plusMinutes(5);
                memory.confirm(confirmedAt);

                repository.save(memory);

                PersonalMemory restored = repository.findById(memory.memoryId()).orElseThrow();

                assertEquals(memory.memoryId(), restored.memoryId());
                assertEquals(memory.content(), restored.content());
                assertEquals(memory.category(), restored.category());
                assertEquals(memory.captureType(), restored.captureType());
                assertEquals(memory.sensitivityLevel(), restored.sensitivityLevel());
                assertEquals(memory.state(), restored.state());
                assertEquals(memory.version(), restored.version());
                assertEquals(memory.createdAt(), restored.createdAt());
                assertEquals(memory.updatedAt(), restored.updatedAt());
                assertEquals(confirmedAt, restored.confirmedAt());
        }

        @Test
        void findByIdReturnsEmptyWhenMemoryDoesNotExist() {
                assertTrue(repository.findById(MemoryId.generate()).isEmpty());
        }

        @Test
        void findByIdRestoresNullConfirmedAtWhenAttributeIsAbsent() {
                PersonalMemory memory = sampleMemory();
                repository.save(memory);

                PersonalMemory restored = repository.findById(memory.memoryId()).orElseThrow();

                assertNull(restored.confirmedAt());
        }

    @Test
    void duplicateSaveIsRejectedWithoutOverwritingExistingItems() {
        PersonalMemory memory = sampleMemory();
        repository.save(memory);

        assertThrows(RuntimeException.class, () -> repository.save(memory));

        Map<String, AttributeValue> memoryItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("META").build())))
                .item();

        Map<String, AttributeValue> revisionItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("REVISION#00000000000000000001").build())))
                .item();

        assertNotNull(memoryItem);
        assertNotNull(revisionItem);
        assertEquals("1", memoryItem.get("version").n());
        assertEquals("CREATE", revisionItem.get("changeType").s());
    }

    @Test
    void saveDoesNotLeaveMemoryWhenInitialRevisionAlreadyExists() {
        PersonalMemory memory = sampleMemory();

        client.putItem(request -> request
                .tableName(tableName)
                .item(Map.of(
                        "pk", AttributeValue.builder()
                                .s("MEMORY#" + memory.memoryId().value())
                                .build(),
                        "sk", AttributeValue.builder()
                                .s("REVISION#00000000000000000001")
                                .build())));

        assertThrows(RuntimeException.class, () -> repository.save(memory));

        Map<String, AttributeValue> memoryItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder()
                                .s("MEMORY#" + memory.memoryId().value())
                                .build(),
                        "sk", AttributeValue.builder()
                                .s("META")
                                .build())))
                .item();

        assertEquals(true, memoryItem.isEmpty());;
    }

    @Test
    void updateAppliesChangesIncrementsVersionAndWritesRevision() {
        PersonalMemory memory = sampleMemory();
        repository.save(memory);

        OffsetDateTime changedAt = memory.updatedAt().plusMinutes(5);
        memory.update(
                new MemoryContent("Project Alice now also uses Dart."),
                MemoryCategory.PROJECT,
                SensitivityLevel.SENSITIVE,
                MemoryState.RESOLVED,
                changedAt);

        repository.update(memory, 1L);

        PersonalMemory restored = repository.findById(memory.memoryId()).orElseThrow();
        assertEquals("Project Alice now also uses Dart.", restored.content().value());
        assertEquals(MemoryCategory.PROJECT, restored.category());
        assertEquals(SensitivityLevel.SENSITIVE, restored.sensitivityLevel());
        assertEquals(MemoryState.RESOLVED, restored.state());
        assertEquals(2, restored.version());
        assertEquals(changedAt, restored.updatedAt());
        assertEquals(changedAt, restored.confirmedAt());

        Map<String, AttributeValue> revisionItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("REVISION#00000000000000000002").build())))
                .item();

        assertNotNull(revisionItem);
        assertEquals("MEMORY_REVISION", revisionItem.get("itemType").s());
        assertEquals("1", revisionItem.get("beforeVersion").n());
        assertEquals("2", revisionItem.get("afterVersion").n());
        assertEquals("UPDATE", revisionItem.get("changeType").s());
    }

    @Test
    void updateRevisionSnapshotsReflectConfirmedAtAsChangedAt() {
        PersonalMemory memory = sampleMemory();
        repository.save(memory);

        OffsetDateTime changedAt = memory.updatedAt().plusMinutes(5);
        memory.update(
                new MemoryContent("Project Alice now also uses Dart."),
                memory.category(),
                memory.sensitivityLevel(),
                memory.state(),
                changedAt);

        repository.update(memory, 1L);

        Map<String, AttributeValue> revisionItem = client.getItem(request -> request
                .tableName(tableName)
                .key(Map.of(
                        "pk", AttributeValue.builder().s("MEMORY#" + memory.memoryId().value()).build(),
                        "sk", AttributeValue.builder().s("REVISION#00000000000000000002").build())))
                .item();

        assertNotNull(revisionItem);
        assertNull(revisionItem.get("beforeSnapshot").m().get("confirmedAt"));
        assertEquals(
                changedAt.toString(),
                revisionItem.get("afterSnapshot").m().get("confirmedAt").s());
    }

    @Test
    void updateWithStaleExpectedVersionDoesNotChangePersistedMemory() {
        PersonalMemory memory = sampleMemory();
        repository.save(memory);

        OffsetDateTime firstChangeAt = memory.updatedAt().plusMinutes(5);
        memory.update(
                new MemoryContent("First revision content."),
                memory.category(),
                memory.sensitivityLevel(),
                memory.state(),
                firstChangeAt);
        repository.update(memory, 1L);

        PersonalMemory staleAttempt = PersonalMemory.reconstitute(
                memory.memoryId(),
                new MemoryContent("Attempted stale update."),
                memory.category(),
                memory.captureType(),
                memory.sensitivityLevel(),
                memory.state(),
                2,
                memory.createdAt(),
                firstChangeAt.plusMinutes(5),
                firstChangeAt.plusMinutes(5));

        assertThrows(MemoryUpdateConflictException.class,
                () -> repository.update(staleAttempt, 1L));

        PersonalMemory restored = repository.findById(memory.memoryId()).orElseThrow();
        assertEquals(2, restored.version());
        assertEquals("First revision content.", restored.content().value());
    }

    @Test
    void updateDoesNotChangeMemoryWhenRevisionAlreadyExists() {
        PersonalMemory memory = sampleMemory();
        repository.save(memory);

        client.putItem(request -> request
                .tableName(tableName)
                .item(Map.of(
                        "pk", AttributeValue.builder()
                                .s("MEMORY#" + memory.memoryId().value())
                                .build(),
                        "sk", AttributeValue.builder()
                                .s("REVISION#00000000000000000002")
                                .build())));

        OffsetDateTime changedAt = memory.updatedAt().plusMinutes(5);
        memory.update(
                new MemoryContent("Should not be persisted."),
                memory.category(),
                memory.sensitivityLevel(),
                memory.state(),
                changedAt);

        assertThrows(MemoryUpdateConflictException.class, () -> repository.update(memory, 1L));
        PersonalMemory restored = repository.findById(memory.memoryId()).orElseThrow();
        assertEquals(1, restored.version());
        assertEquals(memory.createdAt(), restored.updatedAt());
    }

    private static PersonalMemory sampleMemory() {
        return PersonalMemory.create(
                new MemoryContent("Project Alice uses Java."),
                MemoryCategory.ENGINEERING,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                OffsetDateTime.ofInstant(
                        Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC));
    }
}
