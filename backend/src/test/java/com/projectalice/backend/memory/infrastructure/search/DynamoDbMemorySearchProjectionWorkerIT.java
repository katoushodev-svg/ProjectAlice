package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbPersonalMemoryRepository;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchPostingStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageStore;
import java.net.URI;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

class DynamoDbMemorySearchProjectionWorkerIT {
    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>("amazon/dynamodb-local:3.3.0")
            .withExposedPorts(8000)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
    private static DynamoDbClient client;
    private static String tableName;

    @BeforeAll
    static void setUp() {
        DYNAMODB.start();
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .build();
        tableName = "alice-projection-test-" + UUID.randomUUID();
        client.createTable(CreateTableRequest.builder().tableName(tableName)
                .keySchema(KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build());
        client.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(tableName).build());
    }

    @BeforeEach
    void clearPendingIntentPartition() {
        queryPending().forEach(item -> client.deleteItem(request -> request.tableName(tableName).key(Map.of(
                "pk", item.get("pk"), "sk", item.get("sk")))));
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            try { client.deleteTable(request -> request.tableName(tableName)); }
            catch (ResourceNotFoundException ignored) { /* already removed */ }
            finally { client.close(); }
        }
        DYNAMODB.stop();
    }

    @Test
    void createIntentIsClaimedProjectedReadBackVerifiedAndActivated() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(11), System.currentTimeMillis()));

        var metadata = get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META");
        assertEquals("1", metadata.get("activeSemanticVersion").n());
        assertEquals("READY", metadata.get("status").s());
        assertEquals("1", metadata.get("tokenKeyVersion").n());
        assertEquals("1", metadata.get("projectionDigestKeyVersion").n());
        assertFalse(metadata.get("projectionDigest").s().isBlank());

        var pages = fixture.pageStore().readAll(fixture.builder().build(
                fixture.memory().memoryId(), 1, fixture.memory().content()));
        assertEquals(1, pages.size());
        assertFalse(hasPending(fixture.memory().memoryId().value()));
    }

    @Test
    void semanticUpdateCreatesHistoricalPostingAndActivatesNewCurrentProjection() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(21), System.currentTimeMillis()));

        var oldProjection = fixture.builder().build(fixture.memory().memoryId(), 1, fixture.memory().content());
        var oldLexical = oldProjection.pages().stream().flatMap(page -> page.terms().stream())
                .filter(term -> term.termKind() == KwV1Analyzer.TermKind.LEXICAL).findFirst().orElseThrow();
        fixture.memory().update(new MemoryContent("zebrapineapple."), fixture.memory().category(),
                fixture.memory().sensitivityLevel(), fixture.memory().state(), fixture.memory().updatedAt().plusMinutes(1));
        fixture.repository().update(fixture.memory(), 1L);

        assertTrue(fixture.worker().processOne(owner(22), System.currentTimeMillis()));

        var metadata = get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META");
        assertEquals("2", metadata.get("activeSemanticVersion").n());
        assertEquals("READY", metadata.get("status").s());
        assertFalse(hasPending(fixture.memory().memoryId().value()));
        var historicalPosting = get("MEMORY_SEARCH#TERM#1#HISTORICAL#" + oldLexical.termDigest(),
                "MEMORY#" + fixture.memory().memoryId().value() + "#REVISION#00000000000000000001");
        assertEquals("1", historicalPosting.get("revisionNumber").n());
        assertTrue(get("MEMORY_SEARCH#TERM#1#CURRENT#" + oldLexical.termDigest(),
                "MEMORY#" + fixture.memory().memoryId().value()).isEmpty());
        assertFalse(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(),
                "VERSION#00000000000000000001#TERMS#0000").isEmpty(),
                "the historical version page must remain available");
        var currentProjection = fixture.builder().build(fixture.memory().memoryId(), 2, fixture.memory().content());
        var newLexical = currentProjection.pages().stream().flatMap(page -> page.terms().stream())
                .filter(term -> term.termKind() == KwV1Analyzer.TermKind.LEXICAL).findFirst().orElseThrow();
        assertEquals("2", get("MEMORY_SEARCH#TERM#1#CURRENT#" + newLexical.termDigest(),
                "MEMORY#" + fixture.memory().memoryId().value()).get("semanticVersion").n());
    }

    @Test
    void semanticUpdateTakeoverRebuildsAndActivatesFromAuthoritativeRevision() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(29), System.currentTimeMillis()));
        fixture.memory().update(new MemoryContent("takeover revision content."), fixture.memory().category(),
                fixture.memory().sensitivityLevel(), fixture.memory().state(), fixture.memory().updatedAt().plusMinutes(1));
        fixture.repository().update(fixture.memory(), 1L);

        long now = System.currentTimeMillis();
        var intents = new DynamoDbMemorySearchIntentStore(client, tableName);
        var available = intents.findAvailable(now, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s()))
                .findFirst().orElseThrow();
        intents.claimAvailable(available, owner(30), now - DynamoDbMemorySearchIntentStore.LEASE_MILLIS - 1);

        assertTrue(fixture.worker().processOne(owner(31), now));
        assertEquals("2", get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META")
                .get("activeSemanticVersion").n());
        assertFalse(hasPending(fixture.memory().memoryId().value()));
    }

    @Test
    void mismatchedSemanticRevisionDoesNotAdvanceActiveProjection() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(23), System.currentTimeMillis()));
        fixture.memory().update(new MemoryContent("revision target changed."), fixture.memory().category(),
                fixture.memory().sensitivityLevel(), fixture.memory().state(), fixture.memory().updatedAt().plusMinutes(1));
        fixture.repository().update(fixture.memory(), 1L);
        client.updateItem(request -> request.tableName(tableName)
                .key(Map.of("pk", string("MEMORY#" + fixture.memory().memoryId().value()),
                        "sk", string("REVISION#00000000000000000002")))
                .updateExpression("SET afterVersion = :wrong")
                .expressionAttributeValues(Map.of(":wrong", number(3))));

        assertThrows(IllegalStateException.class,
                () -> fixture.worker().processOne(owner(24), System.currentTimeMillis()));
        assertEquals("1", get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META")
                .get("activeSemanticVersion").n());
        assertEquals("CLAIMED", onlyPending(fixture.memory().memoryId().value()).get("status").s());
    }

    @Test
    void activationTransactionFailureLeavesPriorMetadataAndClaimedIntent() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(25), System.currentTimeMillis()));
        fixture.memory().update(new MemoryContent("activation transaction failure target."),
                fixture.memory().category(), fixture.memory().sensitivityLevel(), fixture.memory().state(),
                fixture.memory().updatedAt().plusMinutes(1));
        fixture.repository().update(fixture.memory(), 1L);
        var failingClient = failSecondTransaction(client);

        assertThrows(TransactionCanceledException.class,
                () -> fixture.worker(failingClient).processOne(owner(26), System.currentTimeMillis()));
        var metadata = get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META");
        assertEquals("1", metadata.get("activeSemanticVersion").n());
        assertEquals("READY", metadata.get("status").s());
        var intent = onlyPending(fixture.memory().memoryId().value());
        assertEquals("CLAIMED", intent.get("status").s());
        assertEquals("2", intent.get("targetVersion").n());
    }

    @Test
    void claimedIntentAlreadyCoveredByActiveVersionIsRemovedWithFencedCondition() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(27), System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        String createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String intentId = UUID.randomUUID().toString();
        client.putItem(request -> request.tableName(tableName).item(Map.of(
                "pk", string("MEMORY_SEARCH#PENDING"),
                "sk", string("AVAILABLE#%013d#%013d#%s#%020d".formatted(now, now,
                        fixture.memory().memoryId().value(), 1)),
                "itemType", string("MEMORY_SEARCH_INTENT"), "operation", string("UPSERT"),
                "intentId", string(intentId), "memoryId", string(fixture.memory().memoryId().value()),
                "targetVersion", number(1), "createdAt", string(createdAt),
                "attemptCount", number(0), "nextAttemptAt", number(now))));
        var intents = new DynamoDbMemorySearchIntentStore(client, tableName);
        var available = intents.findAvailable(now, 25).stream()
                .filter(item -> intentId.equals(item.get("intentId").s())).findFirst().orElseThrow();
        var claim = intents.claimAvailable(available, owner(28), now);

        intents.completeSuperseded(claim, 1, System.currentTimeMillis());

        assertEquals("1", get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META")
                .get("activeSemanticVersion").n());
        assertFalse(hasPending(fixture.memory().memoryId().value()));
    }

    @Test
    void retryBecomesAvailableAtScheduledTimeAndNextClaimAdvancesEpoch() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var intents = new DynamoDbMemorySearchIntentStore(client, tableName);
        long now = System.currentTimeMillis();
        var available = intents.findAvailable(now, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s()))
                .findFirst().orElseThrow();
        var firstClaim = intents.claimAvailable(available, owner(32), now);
        long retryAt = now + 30_000;

        intents.retry(firstClaim, retryAt, now + 1);
        assertThrows(TransactionCanceledException.class,
                () -> intents.retry(firstClaim, retryAt, now + 1),
                "replaying a completed retry cannot create a second deferred copy");

        assertTrue(intents.findAvailable(retryAt - 1, 25).stream()
                .noneMatch(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())));
        var deferred = intents.findAvailable(retryAt, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s()))
                .findFirst().orElseThrow();
        assertEquals("1", deferred.get("attemptCount").n());
        var retryClaim = intents.claimAvailable(deferred, owner(33), retryAt);
        assertEquals("1", retryClaim.item().get("attemptCount").n());
        assertEquals("2", retryClaim.item().get("claimEpoch").n());
        assertEquals(firstClaim.item().get("intentId"), retryClaim.item().get("intentId"));
    }

    @Test
    void managementReadinessCountsAvailableClaimedAndRetryDeferredIntentsUntilActivation() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var intents = new DynamoDbMemorySearchIntentStore(client, tableName);
        long now = System.currentTimeMillis();
        assertTrue(intents.hasPendingIntents());

        var available = intents.findAvailable(now, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s()))
                .findFirst().orElseThrow();
        var claim = intents.claimAvailable(available, owner(37), now);
        assertTrue(intents.hasPendingIntents(), "a live claim remains incomplete work");
        long retryAt = now + 5_000;
        intents.retry(claim, retryAt, now + 1);
        assertTrue(intents.hasPendingIntents(), "a deferred retry remains incomplete work");
        assertTrue(intents.findAvailable(retryAt, 25).stream()
                .anyMatch(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())));

        assertTrue(fixture.worker().processOne(owner(38), retryAt));

        var metadata = get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META");
        assertEquals("READY", metadata.get("status").s());
        assertFalse(intents.hasPendingIntents(), "readiness clears only after activation removes the intent");
    }

    @Test
    void workerRemovesOnlyDueSupersededIntentsForActivatedMemory() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        assertTrue(fixture.worker().processOne(owner(34), System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        String memoryId = fixture.memory().memoryId().value();
        String otherMemoryId = "7f1f8e2a-4b3c-4d5e-8f60-123456789abc";
        for (int index = 0; index < 27; index++) {
            putAvailableIntent(memoryId, 1, now, now + index,
                    "00000000-0000-4000-8000-%012d".formatted(100 + index));
        }
        putAvailableIntent(memoryId, 2, now, now + 30, "00000000-0000-4000-8000-000000000044");
        putAvailableIntent(otherMemoryId, 1, now, now + 31, "00000000-0000-4000-8000-000000000045");

        assertTrue(fixture.worker().processOne(owner(35), now + 1));

        var ownPending = queryPending().stream().filter(item -> memoryId.equals(item.get("memoryId").s())).toList();
        assertEquals(3, ownPending.size(), "each pass removes no more than 25 superseded intents");
        assertEquals(2, ownPending.stream().filter(item -> "1".equals(item.get("targetVersion").n())).count());
        assertEquals(1, ownPending.stream().filter(item -> "2".equals(item.get("targetVersion").n())).count());
        assertTrue(new DynamoDbMemorySearchIntentStore(client, tableName).hasPendingIntents(),
                "a newer or unrelated intent keeps Management Search incomplete");
        var unrelatedPending = queryPending().stream()
                .filter(item -> otherMemoryId.equals(item.get("memoryId").s())).toList();
        assertEquals(1, unrelatedPending.size());
        assertEquals("1", unrelatedPending.getFirst().get("targetVersion").n());

        assertTrue(fixture.worker().processOne(owner(36), now + 2));
        ownPending = queryPending().stream().filter(item -> memoryId.equals(item.get("memoryId").s())).toList();
        assertEquals(1, ownPending.size());
        assertEquals("2", ownPending.getFirst().get("targetVersion").n());
        deletePendingForMemory(memoryId);
        deletePendingForMemory(otherMemoryId);
        assertFalse(new DynamoDbMemorySearchIntentStore(client, tableName).hasPendingIntents());
    }

    @Test
    void digestMismatchAfterStrongReadDoesNotActivateOrDeleteClaimedIntent() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var transactions = new AtomicInteger();
        var corruptingClient = clientProxy(client, (method, args, result) -> {
            if (method.equals("transactWriteItems")) transactions.incrementAndGet();
            if (method.equals("getItem") && args[0] instanceof GetItemRequest request
                    && isProjectionPage(request, fixture.memory().memoryId().value())
                    && ((GetItemResponse) result).item().containsKey("descriptors")) {
                var changed = new HashMap<>(((GetItemResponse) result).item());
                var descriptors = new ArrayList<>(changed.get("descriptors").l());
                var first = new HashMap<>(descriptors.get(0).m());
                first.put("termDigest", string("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE"));
                descriptors.set(0, AttributeValue.builder().m(first).build());
                changed.put("descriptors", AttributeValue.builder().l(descriptors).build());
                return GetItemResponse.builder().item(changed).build();
            }
            return result;
        });
        var worker = fixture.worker(corruptingClient);

        assertThrows(IllegalStateException.class, () -> worker.processOne(owner(12), System.currentTimeMillis()));
        assertEquals(1, transactions.get(), "only the claim transaction should run; activation must not be attempted");
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty());
        var intent = onlyPending(fixture.memory().memoryId().value());
        assertEquals("CLAIMED", intent.get("status").s());
        assertEquals("1", intent.get("claimEpoch").n());
    }

    @Test
    void malformedStronglyReadPageDoesNotActivateOrDeleteClaimedIntent() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var corruptingClient = clientProxy(client, (method, args, result) -> {
            if (method.equals("getItem") && args[0] instanceof GetItemRequest request
                    && isProjectionPage(request, fixture.memory().memoryId().value())
                    && ((GetItemResponse) result).item().containsKey("descriptors")) {
                var changed = new HashMap<>(((GetItemResponse) result).item());
                changed.put("pageNumber", number(1));
                return GetItemResponse.builder().item(changed).build();
            }
            return result;
        });

        assertThrows(IllegalArgumentException.class,
                () -> fixture.worker(corruptingClient).processOne(owner(13), System.currentTimeMillis()));
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty());
        assertEquals("CLAIMED", onlyPending(fixture.memory().memoryId().value()).get("status").s());
    }

    @Test
    void failureWritingTermPageAfterPostingWritesKeepsProjectionInactive() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var failingClient = clientProxy(client, (method, args, result) -> {
            if (method.equals("putItem") && args[0] instanceof software.amazon.awssdk.services.dynamodb.model.PutItemRequest request
                    && request.item().get("sk").s().contains("#TERMS#")) {
                throw DynamoDbException.builder().message("injected page write failure").statusCode(500).build();
            }
            return result;
        });

        assertThrows(DynamoDbException.class,
                () -> fixture.worker(failingClient).processOne(owner(14), System.currentTimeMillis()));
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty());
        assertEquals("CLAIMED", onlyPending(fixture.memory().memoryId().value()).get("status").s());
        var projection = fixture.builder().build(fixture.memory().memoryId(), 1, fixture.memory().content());
        assertFalse(projection.pages().get(0).terms().isEmpty());
        var expectedPosting = projection.pages().get(0).terms().get(0);
        var posting = get("MEMORY_SEARCH#TERM#1#CURRENT#" + expectedPosting.termDigest(),
                "MEMORY#" + fixture.memory().memoryId().value());
        assertFalse(posting.isEmpty(), "posting writes before the failed page write should remain staged");
    }

    @Test
    void staleEpochActivationIsRejectedAtomicallyAfterTakeover() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var intentStore = new DynamoDbMemorySearchIntentStore(client, tableName);
        long firstClaimAt = System.currentTimeMillis();
        var available = intentStore.findAvailable(firstClaimAt, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())).findFirst().orElseThrow();
        var oldClaim = intentStore.claimAvailable(available, owner(15), firstClaimAt);
        long takeoverAt = oldClaim.leaseExpiresAtEpochMillis();
        var expired = intentStore.findTakeovers(takeoverAt, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())).findFirst().orElseThrow();
        var newClaim = intentStore.takeOver(expired, owner(16), takeoverAt);
        var metadata = metadata(fixture.memory().memoryId().value());

        var wrongOwner = new DynamoDbMemorySearchIntentStore.Claim(newClaim.item(), owner(15),
                newClaim.epoch(), newClaim.leaseExpiresAtEpochMillis());
        assertThrows(TransactionCanceledException.class,
                () -> intentStore.activate(wrongOwner, metadata, takeoverAt + 1));
        var wrongEpoch = new DynamoDbMemorySearchIntentStore.Claim(newClaim.item(), newClaim.ownerId(),
                oldClaim.epoch(), newClaim.leaseExpiresAtEpochMillis());
        assertThrows(TransactionCanceledException.class,
                () -> intentStore.activate(wrongEpoch, metadata, takeoverAt + 1));
        assertThrows(TransactionCanceledException.class,
                () -> intentStore.activate(oldClaim, metadata, takeoverAt + 1));
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty(),
                "the metadata Put must roll back when the fenced intent Delete fails");
        var claimed = onlyPending(fixture.memory().memoryId().value());
        assertEquals(owner(16), claimed.get("claimOwnerId").s());
        assertEquals("2", claimed.get("claimEpoch").n());

        intentStore.activate(newClaim, metadata, takeoverAt + 1);
        assertEquals("1", get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META")
                .get("activeSemanticVersion").n());
        assertFalse(hasPending(fixture.memory().memoryId().value()));
    }

    @Test
    void activationAtLeaseExpiryBoundaryIsRejectedWithoutMetadataWrite() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var intentStore = new DynamoDbMemorySearchIntentStore(client, tableName);
        long claimAt = System.currentTimeMillis();
        var available = intentStore.findAvailable(claimAt, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())).findFirst().orElseThrow();
        var claim = intentStore.claimAvailable(available, owner(17), claimAt);

        assertThrows(TransactionCanceledException.class,
                () -> intentStore.activate(claim, metadata(fixture.memory().memoryId().value()),
                        claim.leaseExpiresAtEpochMillis()));
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty());
        assertEquals("CLAIMED", onlyPending(fixture.memory().memoryId().value()).get("status").s());
    }

    @Test
    void alreadyClaimedIntentCannotBeClaimedAgainAndWorkerDoesNoProjectionWork() {
        var fixture = fixture(client);
        fixture.repository().save(fixture.memory());
        var intents = new DynamoDbMemorySearchIntentStore(client, tableName);
        long now = System.currentTimeMillis();
        var staleAvailable = intents.findAvailable(now, 25).stream()
                .filter(item -> fixture.memory().memoryId().value().equals(item.get("memoryId").s())).findFirst().orElseThrow();
        intents.claimAvailable(staleAvailable, owner(18), now);
        assertThrows(TransactionCanceledException.class,
                () -> intents.claimAvailable(staleAvailable, owner(19), now));

        assertFalse(fixture.worker().processOne(owner(20), now));
        assertTrue(get("MEMORY_SEARCH#PROJECTION#" + fixture.memory().memoryId().value(), "META").isEmpty());
        assertEquals(owner(18), onlyPending(fixture.memory().memoryId().value()).get("claimOwnerId").s());
    }

    private record Fixture(PersonalMemory memory, DynamoDbPersonalMemoryRepository repository,
            DynamoDbMemorySearchTermPageStore pageStore, KwV1MemoryProjectionBuilder builder,
            MemoryProjectionDigest projectionDigest) {
        DynamoDbMemorySearchProjectionWorker worker() { return worker(client); }
        DynamoDbMemorySearchProjectionWorker worker(DynamoDbClient workerClient) {
            return new DynamoDbMemorySearchProjectionWorker(workerClient, tableName,
                    new DynamoDbMemorySearchIntentStore(workerClient, tableName),
                    new DynamoDbMemorySearchTermPageStore(workerClient, tableName),
                    new DynamoDbMemorySearchPostingStore(workerClient, tableName), builder, projectionDigest);
        }
    }

    private static Fixture fixture(DynamoDbClient persistenceClient) {
        var termKey = "worker-term-test-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var projectionKey = "worker-projection-test-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var termDigest = new MemorySearchTermDigest(Map.of(1, termKey), 1);
        var projectionDigest = new MemoryProjectionDigest(new MemoryProjectionDigestCanonicalEncoder(),
                Map.of(1, projectionKey), 1);
        var builder = new KwV1MemoryProjectionBuilder(new KwV1Analyzer(), termDigest);
        var memory = PersonalMemory.create(new MemoryContent("Project Alice uses Java."),
                MemoryCategory.ENGINEERING, CaptureType.EXPLICIT, SensitivityLevel.NORMAL,
                OffsetDateTime.now(ZoneOffset.UTC));
        return new Fixture(memory, new DynamoDbPersonalMemoryRepository(persistenceClient, tableName),
                new DynamoDbMemorySearchTermPageStore(persistenceClient, tableName), builder, projectionDigest);
    }

    private interface ResponseMutation {
        Object apply(String method, Object[] args, Object result) throws Throwable;
    }

    private static DynamoDbClient clientProxy(DynamoDbClient delegate, ResponseMutation mutation) {
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(delegate, args);
                        return mutation.apply(method.getName(), args, result);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static DynamoDbClient failSecondTransaction(DynamoDbClient delegate) {
        var transactionCount = new AtomicInteger();
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("transactWriteItems")
                            && args != null && args[0] instanceof TransactWriteItemsRequest
                            && transactionCount.incrementAndGet() == 2) {
                        throw TransactionCanceledException.builder().message("injected activation failure").build();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static boolean isProjectionPage(GetItemRequest request, String memoryId) {
        return request.key().get("pk").s().equals("MEMORY_SEARCH#PROJECTION#" + memoryId)
                && request.key().get("sk").s().contains("#TERMS#");
    }

    private static void putAvailableIntent(String memoryId, long targetVersion, long nextAttemptAt,
            long createdAtEpochMillis, String intentId) {
        client.putItem(request -> request.tableName(tableName).item(Map.of(
                "pk", string("MEMORY_SEARCH#PENDING"),
                "sk", string("AVAILABLE#%013d#%013d#%s#%020d".formatted(
                        nextAttemptAt, createdAtEpochMillis, memoryId, targetVersion)),
                "itemType", string("MEMORY_SEARCH_INTENT"), "operation", string("UPSERT"),
                "intentId", string(intentId), "memoryId", string(memoryId),
                "targetVersion", number(targetVersion),
                "createdAt", string(OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(createdAtEpochMillis), ZoneOffset.UTC).toString()),
                "attemptCount", number(0), "nextAttemptAt", number(nextAttemptAt))));
    }

    private static Map<String, AttributeValue> onlyPending(String memoryId) {
        var matches = queryPending().stream()
                .filter(item -> memoryId.equals(item.get("memoryId").s())).toList();
        assertEquals(1, matches.size());
        return matches.get(0);
    }

    private static boolean hasPending(String memoryId) {
        return queryPending().stream().anyMatch(item -> memoryId.equals(item.get("memoryId").s()));
    }

    private static List<Map<String, AttributeValue>> queryPending() {
        return client.query(request -> request.tableName(tableName).keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", "pk"))
                .expressionAttributeValues(Map.of(":pk", string("MEMORY_SEARCH#PENDING")))
                .consistentRead(true)).items();
    }

    private static void deletePendingForMemory(String memoryId) {
        queryPending().stream().filter(item -> memoryId.equals(item.get("memoryId").s()))
                .forEach(item -> client.deleteItem(request -> request.tableName(tableName).key(Map.of(
                        "pk", item.get("pk"), "sk", item.get("sk")))));
    }

    private static Map<String, AttributeValue> metadata(String memoryId) {
        return Map.of("pk", string("MEMORY_SEARCH#PROJECTION#" + memoryId), "sk", string("META"),
                "activeSemanticVersion", number(1));
    }

    private static String owner(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }

    private static AttributeValue string(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue number(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    private static Map<String, AttributeValue> get(String pk, String sk) {
        return client.getItem(request -> request.tableName(tableName).key(Map.of(
                "pk", AttributeValue.builder().s(pk).build(), "sk", AttributeValue.builder().s(sk).build()))
                .consistentRead(true)).item();
    }
}
