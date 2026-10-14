package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.projectalice.backend.memory.application.MemoryContextBudget;
import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemorySearchSignal;
import com.projectalice.backend.memory.application.MemoryTemporalIntent;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbMemoryResetPointRepository;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchPostingMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

class DynamoDbAnswerMemorySearchAdapterIT {
    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.0")
            .withExposedPorts(8000).waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    private static DynamoDbClient client;
    private static String table;

    @BeforeAll static void setUp() {
        DYNAMODB.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy"))).build();
        table = "alice-answer-search-" + UUID.randomUUID();
        client.createTable(CreateTableRequest.builder().tableName(table)
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()).build());
        client.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(table).build());
    }

    @AfterAll static void tearDown() {
        if (client != null) {
            try { client.deleteTable(request -> request.tableName(table)); }
            catch (ResourceNotFoundException ignored) { }
            finally { client.close(); }
        }
        DYNAMODB.stop();
    }

    @Test void queriesReadyCurrentPostingAndStronglyReadsResetPoint() {
        byte[] key = "synthetic-memory-search-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var digest = new MemorySearchTermDigest(Map.of(1, key), 1);
        MemoryId memoryId = MemoryId.generate();
        String termDigest = digest.digestForWrite("KW_V1", "札幌");
        client.putItem(request -> request.tableName(table).item(DynamoDbMemorySearchPostingMapper.current(
                1, termDigest, memoryId, 1, KwV1Analyzer.TermKind.NGRAM_2)));
        client.putItem(request -> request.tableName(table).item(Map.ofEntries(
                Map.entry("pk", s("MEMORY_SEARCH#PROJECTION#" + memoryId.value())), Map.entry("sk", s("META")),
                Map.entry("itemType", s("MEMORY_SEARCH_PROJECTION")), Map.entry("schemaVersion", n("1")),
                Map.entry("analyzerVersion", s("KW_V1")), Map.entry("status", s("READY")),
                Map.entry("activeSemanticVersion", n("1")), Map.entry("tokenKeyVersion", n("1")),
                Map.entry("projectionDigest", s("A".repeat(43))), Map.entry("projectionDigestKeyVersion", n("1")),
                Map.entry("fallbackRequired", AttributeValue.builder().bool(false).build()))));
        client.putItem(request -> request.tableName(table).item(Map.of(
                "pk", s("MEMORY_SETTINGS#PRIMARY"), "sk", s("RESET_POINT"), "itemType", s("MEMORY_RESET_POINT"),
                "schemaVersion", n("1"), "generation", n("7"))));

        var adapter = new DynamoDbAnswerMemorySearchAdapter(client, table, Optional.of(digest),
                new DynamoDbMemorySearchIntentStore(client, table));
        var result = adapter.findCandidates(new MemoryRetrievalQuery(MemoryRetrievalPurpose.ANSWER_CURRENT,
                "札幌", List.of(), List.of(), List.of(), java.util.Set.of(), MemoryTemporalIntent.UNSPECIFIED,
                MemoryContextBudget.baseline(), "req"));

        assertEquals(SearchCompleteness.COMPLETE, result.completeness());
        assertEquals(List.of(memoryId), result.candidates().stream().map(candidate -> candidate.memoryId()).toList());
        assertEquals(java.util.Set.of(MemorySearchSignal.KEYWORD_MATCH), result.candidates().getFirst().signals());
        assertEquals(7, new DynamoDbMemoryResetPointRepository(client, table).currentGeneration());
        assertFalse(result.candidates().getFirst().signals().isEmpty());
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
