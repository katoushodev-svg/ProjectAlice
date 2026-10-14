package com.projectalice.backend.conversation.infrastructure.persistence.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectalice.backend.conversation.application.ConversationReadService;
import com.projectalice.backend.conversation.application.model.ConversationPersistenceConflictException;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;

class DynamoDbConversationRepositoryIT {
    private static final String PK = "CONVERSATION#PRIMARY";
    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.0")
            .withExposedPorts(8000)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(45)));
    private static DynamoDbClient client;
    private static String table;
    private static DynamoDbConversationRepository repository;

    @BeforeAll
    static void startDynamoDbLocalAndCreateTable() {
        DYNAMODB.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .build();
        table = "alice-test-" + UUID.randomUUID();
        client.createTable(CreateTableRequest.builder().tableName(table)
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build()).build());
        client.waiter().waitUntilTableExists(r -> r.tableName(table));
        client.updateTimeToLive(UpdateTimeToLiveRequest.builder().tableName(table)
                .timeToLiveSpecification(TimeToLiveSpecification.builder().enabled(true).attributeName("expiresAtEpochSeconds").build()).build());
        awaitTtlEnabled();
        repository = new DynamoDbConversationRepository(client, table);
    }

    private static void awaitTtlEnabled() {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            DescribeTimeToLiveResponse response = client.describeTimeToLive(DescribeTimeToLiveRequest.builder().tableName(table).build());
            if (response.timeToLiveDescription() != null && "ENABLED".equals(response.timeToLiveDescription().timeToLiveStatusAsString())) {
                assertThat(response.timeToLiveDescription().attributeName()).isEqualTo("expiresAtEpochSeconds");
                return;
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        throw new AssertionError("DynamoDB Local TTL did not become enabled");
    }

    @AfterAll
    static void stopDynamoDbLocal() {
        if (client != null) {
            try { client.deleteTable(r -> r.tableName(table)); }
            catch (ResourceNotFoundException ignored) { }
            finally { client.close(); }
        }
        DYNAMODB.stop();
    }

    @BeforeEach
    void clearTestTable() {
        // Scan is confined to isolated test data cleanup; production paths never use Scan.
        var page = client.scan(r -> r.tableName(table));
        for (Map<String, AttributeValue> item : page.items()) {
            client.deleteItem(r -> r.tableName(table).key(Map.of("pk", item.get("pk"), "sk", item.get("sk"))));
        }
    }

    @Test
    void startCreatesConversationUserMessageProcessingIdempotencyAndBusyLockAtomically() {
        var start = start("key-start", "first message", 1, now());
        repository.start(start);
        var conversation = repository.findConversation().orElseThrow();
        var idempotency = repository.findIdempotency(start.key()).orElseThrow();
        assertThat(conversation.id()).isEqualTo(start.conversationId());
        assertThat(conversation.lastSequence()).isEqualTo(1);
        assertThat(conversation.activeIdempotencyKey()).isEqualTo(start.key());
        assertThat(conversation.busyLeaseExpiresAtEpochSeconds()).isEqualTo(start.leaseExpiresAtEpochSeconds());
        assertThat(idempotency.state()).isEqualTo("PROCESSING");
        assertThat(idempotency.userMessage()).isEqualTo(start.userMessage());
        assertThat(repository.findLatestMessages(10)).containsExactly(start.userMessage());
    }

    @Test
    void newConversationAppearsOnlyTogetherWithFirstUserMessage() {
        assertThat(repository.findConversation()).isEmpty();
        repository.start(start("key-first", "hello", 1, now()));
        assertThat(repository.findConversation()).isPresent();
        assertThat(repository.findLatestMessages(10)).extracting(ConversationMessage::sequence).containsExactly(1L);
    }

    @Test
    void completedIdempotencyCanBeReusedAsReplayWithoutAddingMessages() {
        var start = start("key-replay", "hello", 1, now());
        repository.start(start);
        var assistant = message("assistant", 2, ConversationMessageRole.ASSISTANT, "hi", now().plusSeconds(1));
        repository.complete(start.key(), assistant);
        // The Port reports a failed reservation attempt; the caller then loads the canonical completed result.
        assertThatThrownBy(() -> repository.start(start("key-replay", "hello", 3, now().plusSeconds(2))))
                .isInstanceOf(ConversationPersistenceConflictException.class);
        var replay = repository.findIdempotency(start.key()).orElseThrow();
        assertThat(replay.state()).isEqualTo("COMPLETED");
        assertThat(replay.userMessage()).isEqualTo(start.userMessage());
        assertThat(replay.assistantMessage()).isEqualTo(assistant);
        assertThat(repository.findLatestMessages(10)).containsExactly(assistant, start.userMessage());
    }

    @Test
    void sameKeyWithDifferentContentIsConflictWithoutPartialWrites() {
        var first = start("key-conflict", "first", 1, now());
        repository.start(first);
        var different = start("key-conflict", "different", 2, now().plusSeconds(1));
        assertThatThrownBy(() -> repository.start(different)).isInstanceOf(ConversationPersistenceConflictException.class);
        assertThat(repository.findConversation().orElseThrow().lastSequence()).isEqualTo(1);
        assertThat(repository.findLatestMessages(10)).containsExactly(first.userMessage());
        assertThat(repository.findIdempotency(first.key()).orElseThrow().userMessage()).isEqualTo(first.userMessage());
    }

    @Test
    void activeConversationReservationRejectsAnotherSendAsBusyWithoutPartialWrites() {
        var first = start("key-busy", "first", 1, now());
        repository.start(first);
        assertThatThrownBy(() -> repository.start(start("key-other", "second", 2, now().plusSeconds(1))))
                .isInstanceOf(ConversationPersistenceConflictException.class);
        assertThat(repository.findIdempotency("key-other")).isEmpty();
        assertThat(repository.findLatestMessages(10)).containsExactly(first.userMessage());
        assertThat(repository.findConversation().orElseThrow().activeIdempotencyKey()).isEqualTo(first.key());
    }

    @Test
    void completePersistsAssistantAndCompletedStateAndReleasesBusyLock() {
        var start = start("key-complete", "question", 1, now());
        repository.start(start);
        var assistant = message("assistant-complete", 2, ConversationMessageRole.ASSISTANT, "answer", now().plusSeconds(2));
        repository.complete(start.key(), assistant);
        assertThat(repository.findIdempotency(start.key()).orElseThrow().state()).isEqualTo("COMPLETED");
        assertThat(repository.findConversation().orElseThrow().lastSequence()).isEqualTo(2);
        assertThat(repository.findConversation().orElseThrow().activeIdempotencyKey()).isNull();
        assertThat(repository.findLatestMessages(10)).containsExactly(assistant, start.userMessage());
    }

    @Test
    void failPreservesUserMessageStoresFailureAndReleasesBusyLock() {
        var start = start("key-fail", "question", 1, now());
        repository.start(start);
        repository.fail(start.key(), "PROVIDER_ERROR", now().plusSeconds(2), now().plusSeconds(3600).toEpochSecond());
        var failed = repository.findIdempotency(start.key()).orElseThrow();
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.failureCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(failed.expiresAtEpochSeconds()).isEqualTo(now().plusSeconds(3600).toEpochSecond());
        assertThat(repository.findConversation().orElseThrow().activeIdempotencyKey()).isNull();
        assertThat(repository.findLatestMessages(10)).containsExactly(start.userMessage());
    }

    @Test
    void expiredRecoveryUsesEstablishedInterruptedOutcomeAndUnlocksConversation() {
        var start = start("key-expired", "question", 1, now());
        repository.start(start);
        repository.recoverExpired(start.key(), now().plusSeconds(301), now().plusSeconds(3600).toEpochSecond());
        var recovered = repository.findIdempotency(start.key()).orElseThrow();
        assertThat(recovered.state()).isEqualTo("FAILED");
        assertThat(recovered.failureCode()).isEqualTo("REQUEST_INTERRUPTED");
        assertThat(repository.findConversation().orElseThrow().activeIdempotencyKey()).isNull();
        assertThat(repository.findLatestMessages(10)).containsExactly(start.userMessage());
    }

    @Test
    void metadataAndMessageReadsRestoreJstValuesAndNewestFirstOrdering() {
        var start = start("key-read", "question", 1, now());
        repository.start(start);
        var assistant = message("assistant-read", 2, ConversationMessageRole.ASSISTANT, "answer", now().plusSeconds(1));
        repository.complete(start.key(), assistant);
        var conversation = repository.findConversation().orElseThrow();
        assertThat(conversation.createdAt()).isEqualTo(now());
        assertThat(conversation.updatedAt()).isEqualTo(assistant.createdAt());
        assertThat(repository.findLatestMessages(10)).containsExactly(assistant, start.userMessage());
    }

    @Test
    void repositoryReadPagesHaveNoGapsOrDuplicatesAndReadServiceCursorHasMore() {
        var first = start("cursor-one", "one", 1, now());
        repository.start(first);
        repository.complete(first.key(), message("two", 2, ConversationMessageRole.ASSISTANT, "two", now().plusSeconds(1)));
        var second = start("cursor-two", "three", 3, now().plusSeconds(2));
        repository.start(second);
        repository.complete(second.key(), message("four", 4, ConversationMessageRole.ASSISTANT, "four", now().plusSeconds(3)));
        var third = start("cursor-three", "five", 5, now().plusSeconds(4));
        repository.start(third);
        repository.complete(third.key(), message("six", 6, ConversationMessageRole.ASSISTANT, "six", now().plusSeconds(5)));

        List<Long> all = new ArrayList<>();
        all.addAll(repository.findLatestMessages(3).stream().map(ConversationMessage::sequence).toList());
        all.addAll(repository.findMessagesBefore(4, 3).stream().map(ConversationMessage::sequence).toList());
        assertThat(all).containsExactly(6L, 5L, 4L, 3L, 2L, 1L);

        var reads = new ConversationReadService(repository, new byte[32]);
        var pageOne = reads.getMessages(2, null);
        var pageTwo = reads.getMessages(2, pageOne.nextCursor());
        var pageThree = reads.getMessages(2, pageTwo.nextCursor());
        assertThat(pageOne.hasMore()).isTrue();
        assertThat(pageOne.messages()).extracting(ConversationMessage::sequence).containsExactly(5L, 6L);
        assertThat(pageTwo.hasMore()).isTrue();
        assertThat(pageTwo.messages()).extracting(ConversationMessage::sequence).containsExactly(3L, 4L);
        assertThat(pageThree.hasMore()).isFalse();
        assertThat(pageThree.nextCursor()).isNull();
        assertThat(pageThree.messages()).extracting(ConversationMessage::sequence).containsExactly(1L, 2L);
    }

    private static ConversationRepository.SendStart start(String key, String content, long sequence, OffsetDateTime at) {
        String id = "user-" + key;
        return new ConversationRepository.SendStart(key, "hash-" + content,
                message(id, sequence, ConversationMessageRole.USER, content, at), at, at.plusMinutes(5).toEpochSecond(), "conversation-1");
    }

    private static ConversationMessage message(String id, long sequence, ConversationMessageRole role, String content, OffsetDateTime at) {
        return new ConversationMessage(id, sequence, role, content, at);
    }

    private static OffsetDateTime now() { return OffsetDateTime.of(2026, 10, 7, 12, 0, 0, 0, ZoneOffset.ofHours(9)); }
}
