package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.AnswerMemorySearchResult;
import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemorySearchCandidate;
import com.projectalice.backend.memory.application.MemorySearchSignal;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/** Reads only ready current projections. It deliberately returns unordered candidate hints. */
public final class DynamoDbAnswerMemorySearchAdapter implements AnswerMemorySearchPort {
    public static final int MAX_POSTINGS = 1_000;
    public static final int MAX_UNIQUE_CANDIDATES = 200;

    private final DynamoDbClient client;
    private final String tableName;
    private final Optional<MemorySearchTermDigest> digest;
    private final KwV1QueryTermDigester termDigester;
    private final DynamoDbMemorySearchIntentStore intents;
    private final CandidateGenerationLatencyMetrics latencyMetrics;

    public DynamoDbAnswerMemorySearchAdapter(DynamoDbClient client, String tableName,
            Optional<MemorySearchTermDigest> digest, DynamoDbMemorySearchIntentStore intents) {
        this(client, tableName, digest, intents, new CandidateGenerationLatencyMetrics());
    }

    public DynamoDbAnswerMemorySearchAdapter(DynamoDbClient client, String tableName,
            Optional<MemorySearchTermDigest> digest, DynamoDbMemorySearchIntentStore intents,
            CandidateGenerationLatencyMetrics latencyMetrics) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.latencyMetrics = Objects.requireNonNull(latencyMetrics, "latencyMetrics must not be null");
        this.termDigester = digest.map(key -> new KwV1QueryTermDigester(new KwV1Analyzer(), key)).orElse(null);
    }

    @Override
    public AnswerMemorySearchResult findCandidates(MemoryRetrievalQuery query) {
        return latencyMetrics.measure(CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                () -> findCandidatesInternal(query), result -> result == null ? null : result.completeness());
    }

    private AnswerMemorySearchResult findCandidatesInternal(MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.purpose() != MemoryRetrievalPurpose.ANSWER_CURRENT || digest.isEmpty())
            return AnswerMemorySearchResult.unavailable();
        try {
            boolean partial = intents.hasPendingIntents();
            var terms = canonicalTerms(
                    termDigester.digestQuery(query.currentUserContent(), query.entityHints()));
            Map<String, CandidateAccumulator> candidates = new LinkedHashMap<>();
            java.util.Set<String> conflictingVersions = new java.util.HashSet<>();
            int postingCount = 0;
            boolean truncated = false;
            outer:
            for (var term : terms) {
                QueryRequest.Builder request = QueryRequest.builder().tableName(tableName)
                        .keyConditionExpression("#pk = :pk")
                        .expressionAttributeNames(Map.of("#pk", "pk"))
                        .expressionAttributeValues(Map.of(":pk", s("MEMORY_SEARCH#TERM#" + term.keyVersion()
                                + "#CURRENT#" + term.termDigest())))
                        .consistentRead(true)
                        .limit(MAX_POSTINGS - postingCount + 1);
                software.amazon.awssdk.services.dynamodb.model.QueryResponse response;
                try { response = client.query(request.build()); }
                catch (RuntimeException failedTerm) { partial = true; break; }
                if (response.items() == null) throw new IllegalStateException("posting query returned no item list");
                for (var posting : response.items()) {
                    postingCount++;
                    if (postingCount > MAX_POSTINGS) { truncated = true; break outer; }
                    String memoryId = requiredString(posting, "memoryId");
                    long version = requiredNumber(posting, "semanticVersion");
                    try {
                        if (!isReadyAtVersion(memoryId, version, term.keyVersion())) { partial = true; continue; }
                    } catch (RuntimeException staleOrUnreadableMetadata) {
                        partial = true;
                        continue;
                    }
                    int postingWeight;
                    try {
                        postingWeight = validatedPostingWeight(posting, term.keyVersion());
                    } catch (RuntimeException invalidPosting) {
                        partial = true;
                        continue;
                    }

                    CandidateAccumulator accumulator = candidates.get(memoryId);
                    if (accumulator == null) {
                        accumulator = new CandidateAccumulator(version);
                        candidates.put(memoryId, accumulator);
                    } else if (accumulator.version != version) {
                        conflictingVersions.add(memoryId);
                        partial = true;
                        continue;
                    }
                    accumulator.record(term.queryTermOrdinal(), postingWeight);
                    if (candidates.size() > MAX_UNIQUE_CANDIDATES) { truncated = true; break outer; }
                }
                if (response.lastEvaluatedKey() != null && !response.lastEvaluatedKey().isEmpty()) partial = true;
            }
            if (truncated) partial = true;
            List<MemorySearchCandidate> result = new ArrayList<>(Math.min(candidates.size(), MAX_UNIQUE_CANDIDATES));
            for (var entry : candidates.entrySet()) {
                if (conflictingVersions.contains(entry.getKey())) continue;
                if (result.size() == MAX_UNIQUE_CANDIDATES) break;
                CandidateAccumulator accumulator = entry.getValue();
                int coverage = accumulator.termWeights.size();
                int weightSum = accumulator.termWeights.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                result.add(new MemorySearchCandidate(
                        new com.projectalice.backend.memory.domain.MemoryId(entry.getKey()),
                        accumulator.version,
                        Set.of(MemorySearchSignal.KEYWORD_MATCH),
                        new com.projectalice.backend.memory.application.MemorySearchEvidence(
                                coverage, weightSum)));
            }
            return new AnswerMemorySearchResult(partial ? SearchCompleteness.PARTIAL : SearchCompleteness.COMPLETE, result);
        } catch (RuntimeException unavailable) {
            return AnswerMemorySearchResult.unavailable();
        }
    }

    /**
     * A digest partition can be reached by descriptors that normalize to the same term.
     * Query it once per key version, retaining the strongest descriptor so one posting
     * cannot inflate distinct-term coverage.
     */
    private static List<KwV1QueryTermDigester.DigestedTerm> canonicalTerms(
            List<KwV1QueryTermDigester.DigestedTerm> terms) {
        Map<String, KwV1QueryTermDigester.DigestedTerm> canonical = new LinkedHashMap<>();
        for (var term : terms) {
            String identity = term.keyVersion() + ":" + term.termDigest();
            canonical.merge(identity, term,
                    (left, right) -> right.weightCode() > left.weightCode() ? right : left);
        }
        return List.copyOf(canonical.values());
    }

    /**
     * Validate the posting's own descriptor. The partition key already binds the
     * digest; termKind is not compared to the query descriptor because equal normalized
     * terms can be emitted under different analyzer kinds.
     */
    private static int validatedPostingWeight(
            Map<String, AttributeValue> posting, int expectedKeyVersion) {
        if (!"MEMORY_SEARCH_POSTING".equals(requiredString(posting, "itemType"))) {
            throw new IllegalStateException("unexpected posting itemType");
        }
        if (requiredNumber(posting, "schemaVersion") != 1) {
            throw new IllegalStateException("unsupported posting schemaVersion");
        }
        if (!KwV1Analyzer.VERSION.equals(requiredString(posting, "analyzerVersion"))) {
            throw new IllegalStateException("unexpected posting analyzerVersion");
        }
        if (requiredNumber(posting, "tokenKeyVersion") != expectedKeyVersion) {
            throw new IllegalStateException("unexpected posting tokenKeyVersion");
        }

        KwV1Analyzer.TermKind kind =
                KwV1Analyzer.TermKind.valueOf(requiredString(posting, "termKind"));
        int weight = Math.toIntExact(requiredNumber(posting, "weightCode"));
        if (weight != kind.weightCode()) {
            throw new IllegalStateException("posting weightCode does not match termKind");
        }
        return weight;
    }

    private static final class CandidateAccumulator {
        private final long version;
        private final Map<Integer, Integer> termWeights = new LinkedHashMap<>();

        private CandidateAccumulator(long version) {
            this.version = version;
        }

        private void record(int queryTermOrdinal, int weight) {
            termWeights.merge(queryTermOrdinal, weight, Math::max);
        }
    }

    private boolean isReadyAtVersion(String memoryId, long version, int tokenKeyVersion) {
        var response = client.getItem(GetItemRequest.builder().tableName(tableName).consistentRead(true)
                .key(Map.of("pk", s("MEMORY_SEARCH#PROJECTION#" + memoryId), "sk", s("META"))).build());
        var item = response.item();
        return item != null && !item.isEmpty()
                && "MEMORY_SEARCH_PROJECTION".equals(requiredString(item, "itemType"))
                && requiredNumber(item, "schemaVersion") == 1
                && "READY".equals(requiredString(item, "status"))
                && KwV1Analyzer.VERSION.equals(requiredString(item, "analyzerVersion"))
                && requiredNumber(item, "activeSemanticVersion") == version
                && requiredNumber(item, "tokenKeyVersion") == tokenKeyVersion
                && requiredString(item, "projectionDigest").matches("[A-Za-z0-9_-]{43}")
                && requiredNumber(item, "projectionDigestKeyVersion") > 0
                && item.get("fallbackRequired") != null && item.get("fallbackRequired").bool() != null
                && !item.get("fallbackRequired").bool();
    }

    private static String requiredString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) throw new IllegalStateException("posting or metadata is missing " + name);
        return value.s();
    }

    private static long requiredNumber(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalStateException("posting or metadata is missing " + name);
        return Long.parseLong(value.n());
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
}
