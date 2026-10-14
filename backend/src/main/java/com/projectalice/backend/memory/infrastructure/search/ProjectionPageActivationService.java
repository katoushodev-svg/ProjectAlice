package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchProjectionMetadataMapper;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchTermPageStore;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Stages term pages, verifies their durable representation, then performs fenced activation. */
public final class ProjectionPageActivationService {
    private final DynamoDbMemorySearchTermPageStore pages;
    private final DynamoDbMemorySearchIntentStore intents;
    private final MemoryProjectionDigest digest;
    private final MemoryProjectionReadBackVerifier verifier;

    public ProjectionPageActivationService(DynamoDbMemorySearchTermPageStore pages,
            DynamoDbMemorySearchIntentStore intents, MemoryProjectionDigest digest) {
        this.pages = Objects.requireNonNull(pages, "pages must not be null");
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
        this.verifier = new MemoryProjectionReadBackVerifier(digest);
    }

    public void stageAndActivate(DynamoDbMemorySearchIntentStore.Claim claim,
            KwV1MemoryProjectionBuilder.Projection projection,
            boolean postingWritesComplete,
            boolean fallbackStateApplied,
            long nowEpochMillis,
            String builtAt) {
        stageAndActivate(claim, projection, postingWritesComplete, fallbackStateApplied,
                () -> nowEpochMillis, builtAt);
    }

    public void stageAndActivate(DynamoDbMemorySearchIntentStore.Claim claim,
            KwV1MemoryProjectionBuilder.Projection projection,
            boolean postingWritesComplete,
            boolean fallbackStateApplied,
            LongSupplier clock,
            String builtAt) {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(projection, "projection must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (!postingWritesComplete || !fallbackStateApplied) {
            throw new IllegalStateException("projection worker prerequisites are incomplete");
        }
        if (!projection.memoryId().value().equals(claim.item().get("memoryId").s())
                || projection.semanticVersion() != Long.parseLong(claim.item().get("targetVersion").n())) {
            throw new IllegalArgumentException("projection does not match claimed intent");
        }
        claim = renewIfNeeded(claim, clock.getAsLong());
        var expectedInput = input(projection);
        var expectedDigest = digest.digestForWrite(expectedInput);
        for (int page = 0; page < projection.pages().size(); page++) pages.putIfAbsentOrSame(projection, page);
        var persisted = pages.readAll(projection);
        if (!verifier.verify(projection, persisted, expectedDigest)) {
            throw new IllegalStateException("persisted projection page digest verification failed");
        }
        var metadata = DynamoDbMemorySearchProjectionMetadataMapper.activationAttributes(
                projection, expectedDigest, Objects.requireNonNull(builtAt, "builtAt must not be null"));
        claim = renewIfNeeded(claim, clock.getAsLong());
        intents.activate(claim, metadata, clock.getAsLong());
    }

    private DynamoDbMemorySearchIntentStore.Claim renewIfNeeded(
            DynamoDbMemorySearchIntentStore.Claim claim, long nowEpochMillis) {
        if (nowEpochMillis >= claim.leaseExpiresAtEpochMillis())
            throw new IllegalStateException("projection intent claim lease expired before Activation");
        if (claim.leaseExpiresAtEpochMillis() - nowEpochMillis <= 60_000L)
            return intents.renew(claim, nowEpochMillis);
        return claim;
    }

    private static MemoryProjectionDigestCanonicalEncoder.Input input(
            KwV1MemoryProjectionBuilder.Projection projection) {
        var canonicalPages = new java.util.ArrayList<MemoryProjectionDigestCanonicalEncoder.Page>();
        for (int pageIndex = 0; pageIndex < projection.pages().size(); pageIndex++) {
            var descriptors = projection.pages().get(pageIndex).terms().stream()
                    .map(term -> new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                            term.termDigest(), term.termKind(), term.weightCode())).toList();
            canonicalPages.add(new MemoryProjectionDigestCanonicalEncoder.Page(pageIndex, descriptors));
        }
        return new MemoryProjectionDigestCanonicalEncoder.Input(projection.memoryId(),
                projection.semanticVersion(), projection.analyzerVersion(), projection.tokenKeyVersion(),
                canonicalPages.size(), projection.termCount(), canonicalPages);
    }
}
