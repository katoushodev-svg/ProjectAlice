package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbPersonalMemoryRepository;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class FindRelevantMemoriesServiceIT {

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
    void hydratesPersistedMemoryFromSearchCandidateAndSkipsMissingOne() {
        PersonalMemory memory = PersonalMemory.create(
                new MemoryContent("Project Alice uses Java."),
                MemoryCategory.ENGINEERING,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                OffsetDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZoneOffset.ofHours(9)));
        repository.save(memory);

        List<MemorySearchCandidate> candidates = List.of(
                new MemorySearchCandidate(MemoryId.generate(), 1, Set.of()),
                new MemorySearchCandidate(
                        memory.memoryId(), memory.version(), Set.of(MemorySearchSignal.KEYWORD_MATCH)));
        AnswerMemorySearchPort searchPort = query -> candidates;
        FindRelevantMemoriesService service = new FindRelevantMemoriesService(searchPort, repository);

        FindRelevantMemoriesResult result = service.execute(new MemoryRetrievalQuery(
                MemoryRetrievalPurpose.ANSWER_CURRENT, "What language does Alice use?",
                null, null, null, null, MemoryTemporalIntent.CURRENT,
                MemoryContextBudget.baseline(), "req-it-1"));

        assertEquals(FindRelevantMemoriesResult.Status.COMPLETE, result.status());
        assertEquals(1, result.items().size());
        MemoryContextItem item = result.items().get(0);
        assertEquals(memory.memoryId(), item.memoryId());
        assertEquals(1, item.memoryVersion());
        assertEquals("Project Alice uses Java.", item.content());
        assertEquals(MemoryCategory.ENGINEERING, item.category());
        assertEquals(MemoryTemporalRole.CURRENT, item.temporalRole());
    }
}
