package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.domain.MemoryId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Bounded DynamoDB base-table reader for the KW_V1 inverted-index Posting items.
 *
 * <p>This class reads Posting records only. It does not hydrate PersonalMemory,
 * validate eligibility, rank candidates, or decide whether a search may return
 * a user-facing answer. SDK exceptions are intentionally propagated so callers
 * cannot accidentally turn a failed read into a complete empty result.</p>
 */
public final class DynamoDbPostingReader {

    public static final String CURRENT_PREFIX = "MEMORY_SEARCH#TERM#";
    public static final String CURRENT_MARKER = "#CURRENT#";
    public static final String HISTORICAL_MARKER = "#HISTORICAL#";

    public record QueryTerm(int tokenKeyVersion, String termDigest, int queryTermOrdinal) {
        public QueryTerm {
            if (tokenKeyVersion < 1) {
                throw new IllegalArgumentException("tokenKeyVersion must be >= 1");
            }
            Objects.requireNonNull(termDigest, "termDigest must not be null");
            if (!termDigest.matches("[A-Za-z0-9_-]{43}")) {
                throw new IllegalArgumentException("termDigest must be a 43-character Base64url SHA-256 digest");
            }
            if (queryTermOrdinal < 0 || queryTermOrdinal >= 32) {
                throw new IllegalArgumentException("queryTermOrdinal must be in [0, 31]");
            }
        }
    }

    public record ReadResult(
            SearchCompleteness completeness,
            List<PostingCandidateAccumulator.PostingObservation> postings,
            int postingEvaluations,
            boolean hasMore) {
        public ReadResult {
            Objects.requireNonNull(completeness, "completeness must not be null");
            postings = List.copyOf(Objects.requireNonNull(postings, "postings must not be null"));
            if (postingEvaluations < 0 || postingEvaluations != postings.size()) {
                throw new IllegalArgumentException("postingEvaluations must equal returned posting count");
            }
            if (completeness == SearchCompleteness.COMPLETE && hasMore) {
                throw new IllegalArgumentException("a result with more postings cannot be COMPLETE");
            }
            if (completeness == SearchCompleteness.UNAVAILABLE && !postings.isEmpty()) {
                throw new IllegalArgumentException("unavailable result cannot contain postings");
            }
        }
    }

    private record StreamSpec(String partitionKey, int queryTermOrdinal, boolean historical) { }

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbPostingReader(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = Objects.requireNonNull(dynamoDbClient, "dynamoDbClient must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
    }

    /**
     * Reads Current postings for all purposes. Only ANSWER_HISTORICAL also reads
     * Historical postings. Key versions are supplied by the caller, which owns
     * the temporary rotation policy (normally at most old+new).
     *
     * <p>The method uses strongly consistent base-table Query calls and enforces
     * one aggregate posting-evaluation budget across all term/key-version streams.
     * If a stream has another page or an unvisited stream remains when the budget
     * is exhausted, the result is PARTIAL.</p>
     */
    public ReadResult read(
            MemoryRetrievalPurpose purpose,
            List<QueryTerm> queryTerms) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        List<QueryTerm> terms = List.copyOf(Objects.requireNonNull(queryTerms, "queryTerms must not be null"));
        if (terms.size() > 32) {
            throw new IllegalArgumentException("queryTerms must not exceed 32");
        }
        if (terms.isEmpty()) {
            return new ReadResult(SearchCompleteness.COMPLETE, List.of(), 0, false);
        }

        int postingLimit = switch (purpose) {
            case MANAGEMENT_SEARCH -> PostingCandidateAccumulator.MANAGEMENT_POSTING_LIMIT;
            case ANSWER_CURRENT, ANSWER_HISTORICAL, RELATION_DECISION ->
                    PostingCandidateAccumulator.ANSWER_POSTING_LIMIT;
        };

        List<StreamSpec> streams = buildStreams(purpose, terms);
        List<PostingCandidateAccumulator.PostingObservation> results = new ArrayList<>();
        int evaluated = 0;
        boolean hasMore = false;

        for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
            StreamSpec stream = streams.get(streamIndex);
            Map<String, AttributeValue> exclusiveStartKey = null;

            do {
                int remaining = postingLimit - evaluated;
                if (remaining <= 0) {
                    hasMore = true;
                    break;
                }

                QueryRequest.Builder request = QueryRequest.builder()
                        .tableName(tableName)
                        .keyConditionExpression("pk = :pk")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(stream.partitionKey()).build()))
                        .consistentRead(true)
                        .limit(remaining);

                if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                    request.exclusiveStartKey(exclusiveStartKey);
                }

                QueryResponse response = dynamoDbClient.query(request.build());
                List<Map<String, AttributeValue>> items = response.items();
                for (Map<String, AttributeValue> item : items) {
                    results.add(toObservation(item, stream.queryTermOrdinal(), stream.historical()));
                }
                evaluated += items.size();

                exclusiveStartKey = response.lastEvaluatedKey();
                boolean pageHasMore = exclusiveStartKey != null && !exclusiveStartKey.isEmpty();

                if (evaluated >= postingLimit && (pageHasMore || streamIndex < streams.size() - 1)) {
                    hasMore = true;
                    break;
                }
                if (pageHasMore && evaluated < postingLimit) {
                    continue;
                }
                break;
            } while (true);

            if (hasMore || evaluated >= postingLimit) {
                if (streamIndex < streams.size() - 1) {
                    hasMore = true;
                }
                break;
            }
        }

        return new ReadResult(
                hasMore ? SearchCompleteness.PARTIAL : SearchCompleteness.COMPLETE,
                results,
                evaluated,
                hasMore);
    }

    private static List<StreamSpec> buildStreams(
            MemoryRetrievalPurpose purpose,
            List<QueryTerm> queryTerms) {
        List<StreamSpec> streams = new ArrayList<>();
        Set<String> seenPartitions = new HashSet<>();
        for (QueryTerm term : queryTerms) {
            String prefix = CURRENT_PREFIX + term.tokenKeyVersion();
            addStream(streams, seenPartitions,
                    new StreamSpec(prefix + CURRENT_MARKER + term.termDigest(),
                            term.queryTermOrdinal(), false));
            if (purpose == MemoryRetrievalPurpose.ANSWER_HISTORICAL) {
                addStream(streams, seenPartitions,
                        new StreamSpec(prefix + HISTORICAL_MARKER + term.termDigest(),
                                term.queryTermOrdinal(), true));
            }
        }
        return streams;
    }

    private static void addStream(
            List<StreamSpec> streams,
            Set<String> seenPartitions,
            StreamSpec stream) {
        // Same partition + query ordinal can occur if caller accidentally duplicates a key version/digest.
        String identity = stream.partitionKey() + "\u0000" + stream.queryTermOrdinal();
        if (seenPartitions.add(identity)) {
            streams.add(stream);
        }
    }

    private static PostingCandidateAccumulator.PostingObservation toObservation(
            Map<String, AttributeValue> item,
            int queryTermOrdinal,
            boolean historicalStream) {
        String itemType = requiredString(item, "itemType");
        if (!"MEMORY_SEARCH_POSTING".equals(itemType)) {
            throw new IllegalStateException("Unexpected itemType in search Posting partition");
        }

        MemoryId memoryId = new MemoryId(requiredString(item, "memoryId"));
        PostingCandidateAccumulator.TermKind termKind;
        try {
            termKind = PostingCandidateAccumulator.TermKind.valueOf(requiredString(item, "termKind"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid termKind in search Posting item", exception);
        }

        Long revisionNumber = null;
        Long semanticVersion = null;
        if (historicalStream) {
            revisionNumber = requiredLong(item, "revisionNumber");
            if (item.containsKey("semanticVersion")) {
                throw new IllegalStateException("Historical Posting must not contain semanticVersion");
            }
        } else {
            if (item.containsKey("revisionNumber")) {
                throw new IllegalStateException("Current Posting must not contain revisionNumber");
            }
            semanticVersion = requiredLong(item, "semanticVersion");
        }

        return new PostingCandidateAccumulator.PostingObservation(
                memoryId, revisionNumber, queryTermOrdinal, termKind, semanticVersion);
    }

    private static String requiredString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null || value.s().isBlank()) {
            throw new IllegalStateException("Missing or invalid Posting attribute: " + name);
        }
        return value.s();
    }

    private static long requiredLong(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) {
            throw new IllegalStateException("Missing or invalid Posting attribute: " + name);
        }
        try {
            long parsed = Long.parseLong(value.n());
            if (parsed < 1) {
                throw new NumberFormatException("must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid Posting attribute: " + name, exception);
        }
    }
}
