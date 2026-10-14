package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageMapper;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchPostingStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/** Fail-closed worker for supported Create and Semantic Update projection paths. */
public final class DynamoDbMemorySearchProjectionWorker {
    private final DynamoDbClient client;
    private final String tableName;
    private final DynamoDbMemorySearchIntentStore intents;
    private final DynamoDbMemorySearchTermPageStore pages;
    private final DynamoDbMemorySearchPostingStore postings;
    private final KwV1MemoryProjectionBuilder builder;
    private final ProjectionPageActivationService activation;
    private final LongSupplier clock;

    public DynamoDbMemorySearchProjectionWorker(DynamoDbClient client, String tableName,
            DynamoDbMemorySearchIntentStore intents, DynamoDbMemorySearchTermPageStore pages,
            DynamoDbMemorySearchPostingStore postings, KwV1MemoryProjectionBuilder builder,
            MemoryProjectionDigest projectionDigest) {
        this(client, tableName, intents, pages, postings, builder, projectionDigest, System::currentTimeMillis);
    }

    public DynamoDbMemorySearchProjectionWorker(DynamoDbClient client, String tableName,
            DynamoDbMemorySearchIntentStore intents, DynamoDbMemorySearchTermPageStore pages,
            DynamoDbMemorySearchPostingStore postings, KwV1MemoryProjectionBuilder builder,
            MemoryProjectionDigest projectionDigest, LongSupplier clock) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.pages = Objects.requireNonNull(pages, "pages must not be null");
        this.postings = Objects.requireNonNull(postings, "postings must not be null");
        this.builder = Objects.requireNonNull(builder, "builder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.activation = new ProjectionPageActivationService(pages, intents,
                Objects.requireNonNull(projectionDigest, "projectionDigest must not be null"));
    }

    /** Processes the oldest eligible initial-create intent, or returns false if none was claimed. */
    public boolean processOne(String workerOwnerId, long nowEpochMillis) {
        var candidates = new java.util.ArrayList<IntentCandidate>();
        intents.findAvailable(nowEpochMillis, 25).forEach(item -> candidates.add(new IntentCandidate(item, false)));
        intents.findTakeovers(nowEpochMillis, 25).forEach(item -> candidates.add(new IntentCandidate(item, true)));
        for (IntentCandidate candidate : candidates) {
            final DynamoDbMemorySearchIntentStore.Claim claim;
            try {
                claim = candidate.takeover()
                        ? intents.takeOver(candidate.item(), workerOwnerId, nowEpochMillis)
                        : intents.claimAvailable(candidate.item(), workerOwnerId, nowEpochMillis);
            } catch (TransactionCanceledException claimLost) {
                continue;
            }
            processClaim(claim);
            return true;
        }
        return false;
    }

    private record IntentCandidate(Map<String, AttributeValue> item, boolean takeover) {}

    private void processClaim(DynamoDbMemorySearchIntentStore.Claim claim) {
        if (!"UPSERT".equals(requiredS(claim.item(), "operation")))
            throw new IllegalStateException("only UPSERT projection intents are supported by this worker path");
        MemoryId memoryId = new MemoryId(requiredS(claim.item(), "memoryId"));
        long targetVersion = requiredN(claim.item(), "targetVersion");
        Map<String, AttributeValue> root = strongGet("MEMORY#" + memoryId.value(), "META");
        if (root.isEmpty() || !"PERSONAL_MEMORY".equals(requiredS(root, "itemType"))
                || !memoryId.value().equals(requiredS(root, "memoryId"))
                || requiredN(root, "version") < targetVersion)
            throw new IllegalStateException("authoritative Memory predates the claimed target");

        Revision targetRevision = revision(memoryId, targetVersion);
        if (targetRevision == null) throw new IllegalStateException("claimed target Revision is missing");
        requireTargetRevisionEligible(targetVersion, targetRevision.changeType());
        boolean activeMetadataExists = false;
        long activeVersion = 0;
        Map<String, AttributeValue> activeMetadata = strongGet("MEMORY_SEARCH#PROJECTION#" + memoryId.value(), "META");
        List<KwV1MemoryProjectionBuilder.TermDescriptor> oldCurrentTerms = List.of();
        if (!activeMetadata.isEmpty()) {
            activeMetadataExists = true;
            activeVersion = requiredN(activeMetadata, "activeSemanticVersion");
            if (activeVersion >= targetVersion) {
                claim = renewIfNeeded(claim);
                intents.completeSuperseded(claim, activeVersion, clock.getAsLong());
                cleanupSupersededIntents(memoryId, activeVersion, claim.ownerId(), 24);
                return;
            }
            if (!"MEMORY_SEARCH_PROJECTION".equals(requiredS(activeMetadata, "itemType"))
                    || !memoryId.value().equals(requiredS(activeMetadata, "memoryId"))
                    || !KwV1Analyzer.VERSION.equals(requiredS(activeMetadata, "analyzerVersion")))
                throw new IllegalStateException("active Projection analyzer is not supported");
            Revision activeRevision = revision(memoryId, activeVersion);
            if (activeRevision == null) throw new IllegalStateException("active Projection Revision is missing");
            var activeProjection = builder.build(memoryId, activeVersion, activeRevision.content(),
                    Math.toIntExact(requiredN(activeMetadata, "tokenKeyVersion")));
            requireNoFallback(activeProjection.fallbackRequired());
            List<DynamoDbMemorySearchTermPageMapper.PersistedPage> persisted = pages.readAll(activeProjection);
            int persistedTermCount = persisted.stream().mapToInt(page -> page.descriptors().size()).sum();
            if (persisted.size() != requiredN(activeMetadata, "currentTermPageCount")
                    || persistedTermCount != requiredN(activeMetadata, "currentTermCount")
                    || !activeMetadata.get("fallbackRequired").bool().equals(activeProjection.fallbackRequired()))
                throw new IllegalStateException("active Projection Metadata does not match its Revision Pages");
            var activeTerms = activeProjection.pages().stream().flatMap(page -> page.terms().stream()).toList();
            var readTerms = persisted.stream().flatMap(page -> page.descriptors().stream()).toList();
            if (!activeTerms.equals(readTerms)) throw new IllegalStateException("active Pages differ from authoritative Revision");
            oldCurrentTerms = readTerms;
            for (var descriptor : readTerms) {
                claim = renewIfNeeded(claim);
                postings.putHistorical(activeProjection.tokenKeyVersion(), descriptor.termDigest(), memoryId,
                        activeVersion, descriptor.termKind());
            }
        }

        long firstHistoricalVersion = activeMetadataExists ? activeVersion + 1 : 1;
        for (long version = firstHistoricalVersion; version < targetVersion; version++) {
            Revision historicalRevision = revision(memoryId, version);
            if (historicalRevision == null) continue; // Confirmation-only versions do not create a Revision.
            KwV1MemoryProjectionBuilder.Projection historical = builder.build(memoryId, version,
                    historicalRevision.content());
            requireNoFallback(historical.fallbackRequired());
            for (int pageNumber = 0; pageNumber < historical.pages().size(); pageNumber++) {
                claim = renewIfNeeded(claim);
                pages.putIfAbsentOrSame(historical, pageNumber);
            }
            List<DynamoDbMemorySearchTermPageMapper.PersistedPage> historyReadBack = pages.readAll(historical);
            List<KwV1MemoryProjectionBuilder.TermDescriptor> expectedHistory = historical.pages().stream()
                    .flatMap(page -> page.terms().stream()).toList();
            if (!expectedHistory.equals(historyReadBack.stream()
                    .flatMap(page -> page.descriptors().stream()).toList()))
                throw new IllegalStateException("historical Revision Page read-back differs from its source");
            for (var page : historical.pages()) for (var descriptor : page.terms()) {
                claim = renewIfNeeded(claim);
                postings.putHistorical(historical.tokenKeyVersion(), descriptor.termDigest(), memoryId,
                        version, descriptor.termKind());
            }
        }

        KwV1MemoryProjectionBuilder.Projection projection = builder.build(memoryId, targetVersion,
                targetRevision.content());
        requireNoFallback(projection.fallbackRequired());

        for (var page : projection.pages()) for (var descriptor : page.terms()) {
            claim = renewIfNeeded(claim);
            postings.putCurrent(projection.tokenKeyVersion(), descriptor.termDigest(), memoryId,
                    projection.semanticVersion(), descriptor.termKind());
        }
        claim = renewIfNeeded(claim);
        long builtAtEpochMillis = clock.getAsLong();
        activation.stageAndActivate(claim, projection, true, true, clock,
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(builtAtEpochMillis), ZoneOffset.UTC).toString());

        cleanupSupersededIntents(memoryId, targetVersion, claim.ownerId(), 25);

        if (activeMetadataExists) {
            java.util.Set<String> newTerms = projection.pages().stream().flatMap(page -> page.terms().stream())
                    .map(KwV1MemoryProjectionBuilder.TermDescriptor::termDigest)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (var descriptor : oldCurrentTerms) if (!newTerms.contains(descriptor.termDigest()))
                postings.deleteCurrentIfVersion(Math.toIntExact(requiredN(activeMetadata, "tokenKeyVersion")),
                        descriptor.termDigest(), memoryId, activeVersion);
        }
    }

    /** Deletes at most the remaining per-pass budget of due UPSERT intents covered by the Active version. */
    private void cleanupSupersededIntents(
            MemoryId memoryId, long activeVersion, String ownerId, int remainingDeleteBudget) {
        if (remainingDeleteBudget < 1) return;
        long now = clock.getAsLong();
        var candidates = new java.util.ArrayList<IntentCandidate>();
        intents.findAvailable(now, 25).stream()
                .filter(item -> isSupersededUpsert(item, memoryId, activeVersion))
                .forEach(item -> candidates.add(new IntentCandidate(item, false)));
        intents.findTakeovers(now, 25).stream()
                .filter(item -> isSupersededUpsert(item, memoryId, activeVersion))
                .forEach(item -> candidates.add(new IntentCandidate(item, true)));

        int deleted = 0;
        for (IntentCandidate candidate : candidates) {
            if (deleted >= remainingDeleteBudget) break;
            final DynamoDbMemorySearchIntentStore.Claim staleClaim;
            try {
                staleClaim = candidate.takeover()
                        ? intents.takeOver(candidate.item(), ownerId, clock.getAsLong())
                        : intents.claimAvailable(candidate.item(), ownerId, clock.getAsLong());
            } catch (TransactionCanceledException lostClaimRace) {
                continue;
            }
            try {
                intents.completeSuperseded(staleClaim, activeVersion, clock.getAsLong());
                deleted++;
            } catch (TransactionCanceledException versionOrLeaseChanged) {
                // A concurrent activation or takeover won. Leave the intent for the next worker pass.
            }
        }
    }

    private static boolean isSupersededUpsert(
            Map<String, AttributeValue> item, MemoryId memoryId, long activeVersion) {
        return memoryId.value().equals(item.get("memoryId").s())
                && "UPSERT".equals(item.get("operation").s())
                && requiredN(item, "targetVersion") <= activeVersion;
    }

    private Revision revision(MemoryId memoryId, long version) {
        Map<String, AttributeValue> item = strongGet("MEMORY#" + memoryId.value(),
                "REVISION#%020d".formatted(version));
        if (item.isEmpty()) return null;
        if (!"MEMORY_REVISION".equals(requiredS(item, "itemType"))
                || !memoryId.value().equals(requiredS(item, "memoryId"))
                || requiredN(item, "revisionNumber") != version || requiredN(item, "afterVersion") != version)
            throw new IllegalStateException("Revision identity or target version is inconsistent");
        AttributeValue snapshot = item.get("afterSnapshot");
        if (snapshot == null || !snapshot.hasM()) throw new IllegalStateException("Revision afterSnapshot is invalid");
        Map<String, AttributeValue> after = snapshot.m();
        String changeType = requiredS(item, "changeType");
        requireTargetRevisionEligible(version, changeType);
        return new Revision(changeType, new MemoryContent(requiredS(after, "content")));
    }

    private record Revision(String changeType, MemoryContent content) {}

    private DynamoDbMemorySearchIntentStore.Claim renewIfNeeded(DynamoDbMemorySearchIntentStore.Claim claim) {
        long now = clock.getAsLong();
        if (now >= claim.leaseExpiresAtEpochMillis())
            throw new IllegalStateException("projection intent claim lease expired");
        if (claim.leaseExpiresAtEpochMillis() - now <= 60_000L) return intents.renew(claim, now);
        return claim;
    }

    static void requireInitialCreateEligible(long targetVersion, String changeType, boolean activeMetadataExists) {
        if (targetVersion != 1 || !"CREATE".equals(changeType))
            throw new IllegalStateException("initial projection target must be a CREATE Revision at version 1");
        if (activeMetadataExists)
            throw new IllegalStateException("existing Active Projection requires history-aware worker path");
    }

    static void requireTargetRevisionEligible(long targetVersion, String changeType) {
        if (targetVersion < 1 || (targetVersion == 1 && !"CREATE".equals(changeType))
                || (targetVersion > 1 && !"UPDATE".equals(changeType)))
            throw new IllegalStateException("Revision change type does not match its semantic version");
    }

    static void requireNoFallback(boolean fallbackRequired) {
        if (fallbackRequired) throw new IllegalStateException("Fallback Posting lifecycle is not implemented");
    }

    private Map<String, AttributeValue> strongGet(String pk, String sk) {
        return client.getItem(GetItemRequest.builder().tableName(tableName)
                .key(Map.of("pk", AttributeValue.builder().s(pk).build(),
                        "sk", AttributeValue.builder().s(sk).build()))
                .consistentRead(true).build()).item();
    }

    private static String requiredS(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) throw new IllegalArgumentException("missing attribute " + name);
        return value.s();
    }
    private static long requiredN(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalArgumentException("missing attribute " + name);
        return Long.parseLong(value.n());
    }
}
