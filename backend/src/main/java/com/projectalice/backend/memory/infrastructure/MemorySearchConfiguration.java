package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.FindRelevantMemoriesService;
import com.projectalice.backend.memory.application.FindRelevantMemoriesUseCase;
import com.projectalice.backend.memory.application.MemoryRelevanceRankingPolicy;
import com.projectalice.backend.memory.application.MemoryDeduplicationPolicy;
import com.projectalice.backend.memory.application.MemoryDiversityPolicy;
import com.projectalice.backend.memory.application.port.out.MemoryResetPointRepository;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.application.MemoryContextPayloadSerializer;
import com.projectalice.backend.memory.application.ValidateMemorySelectionService;
import com.projectalice.backend.memory.application.ValidateMemorySelectionUseCase;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbMemoryResetPointRepository;
import com.projectalice.backend.memory.infrastructure.persistence.dynamodb.DynamoDbPersonalMemoryRepository;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbAnswerMemorySearchAdapter;
import com.projectalice.backend.memory.infrastructure.search.MemorySearchTermDigest;
import com.projectalice.backend.memory.infrastructure.search.dynamodb.DynamoDbMemorySearchIntentStore;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Infrastructure wiring for answer search. Key material is confined to the digest capability. */
@Configuration(proxyBeanMethods = false)
public class MemorySearchConfiguration {
    private static final String SEARCH_PROBE = "xByOEBw-3aY0WaUv7Lo4rcHxEp6UonFD6Ht-t3RQo-Q";

    @Bean MemoryResetPointRepository memoryResetPointRepository(DynamoDbClient client,
            @Value("${ALICE_DYNAMODB_TABLE_NAME:project-alice-conversation-local}") String table) {
        return new DynamoDbMemoryResetPointRepository(client, table);
    }

    @Bean MemoryContextPayloadSerializer memoryContextPayloadSerializer() {
        return new MemoryContextPayloadSerializer();
    }

    @Bean PersonalMemoryRepository personalMemoryRepository(DynamoDbClient client,
            @Value("${ALICE_DYNAMODB_TABLE_NAME:project-alice-conversation-local}") String table) {
        return new DynamoDbPersonalMemoryRepository(client, table);
    }

    @Bean ValidateMemorySelectionUseCase validateMemorySelectionUseCase(MemoryResetPointRepository resetPoints,
            PersonalMemoryRepository memories, TokenEstimator estimator, MemoryContextPayloadSerializer serializer) {
        return new ValidateMemorySelectionService(resetPoints, memories, estimator, serializer);
    }

    @Bean FindRelevantMemoriesUseCase findRelevantMemoriesUseCase(AnswerMemorySearchPort searchPort,
            PersonalMemoryRepository memories, TokenEstimator estimator, MemoryContextPayloadSerializer serializer,
            MemoryResetPointRepository resetPoints) {
        // Ranking, deduplication and diversity policies remain deliberately conservative until their
        // acceptance criteria are finalized; this wiring does not introduce new ranking behavior.
        return new FindRelevantMemoriesService(searchPort, memories,
                MemoryRelevanceRankingPolicy.preservingInputOrder(),
                MemoryDeduplicationPolicy.preservingCandidates(),
                MemoryDiversityPolicy.preservingCandidates(), estimator, serializer, resetPoints);
    }

    @Bean MemorySearchTermCapability memorySearchTermCapability(
            @Value("${ALICE_MEMORY_SEARCH_TERM_KEYS:}") String encodedKeys,
            @Value("${ALICE_MEMORY_SEARCH_TERM_WRITE_VERSION:}") String writeVersion) {
        if (encodedKeys.isBlank() || writeVersion.isBlank()) return new MemorySearchTermCapability(Optional.empty());
        try {
            MapBuilder keys = parseKeys(encodedKeys);
            int active = Integer.parseInt(writeVersion);
            MemorySearchTermDigest probe = new MemorySearchTermDigest(
                    Map.of(1, "synthetic-memory-search-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 1);
            if (!SEARCH_PROBE.equals(probe.digestForRead(1, "KW_V1", "札幌")))
                return new MemorySearchTermCapability(Optional.empty());
            MemorySearchTermDigest capability = new MemorySearchTermDigest(keys.values, active);
            if (!capability.digestForRead(active, "KW_V1", "札幌").matches("[A-Za-z0-9_-]{43}"))
                return new MemorySearchTermCapability(Optional.empty());
            return new MemorySearchTermCapability(Optional.of(capability));
        } catch (RuntimeException invalidCapability) {
            return new MemorySearchTermCapability(Optional.empty());
        }
    }

    @Bean AnswerMemorySearchPort answerMemorySearchPort(DynamoDbClient client,
            @Value("${ALICE_DYNAMODB_TABLE_NAME:project-alice-conversation-local}") String table,
            MemorySearchTermCapability capability) {
        return new DynamoDbAnswerMemorySearchAdapter(client, table, capability.digest(),
                new DynamoDbMemorySearchIntentStore(client, table));
    }

    private static MapBuilder parseKeys(String encoded) {
        HashMap<Integer, byte[]> values = new HashMap<>();
        for (String pair : encoded.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid search key configuration");
            int version = Integer.parseInt(parts[0]);
            byte[] key = Base64.getDecoder().decode(parts[1]);
            if (key.length == 0 || values.putIfAbsent(version, key) != null)
                throw new IllegalArgumentException("invalid search key configuration");
        }
        return new MapBuilder(values);
    }

    private record MapBuilder(java.util.Map<Integer, byte[]> values) {}
}
