package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.application.MemoryRelationDecisionService;
import com.projectalice.backend.memory.application.MemoryRelationDecisionUseCase;
import com.projectalice.backend.memory.application.port.out.RelatedMemorySearchPort;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbRelatedMemorySearchAdapter;
import com.projectalice.backend.memory.infrastructure.search.CandidateGenerationLatencyMetrics;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbPostingReader;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbSearchEvidenceReader;
import com.projectalice.backend.memory.infrastructure.search.PostingCandidateAccumulator;
import com.projectalice.backend.memory.infrastructure.search.PostingSearchPipeline;
import com.projectalice.backend.memory.infrastructure.search.SearchCandidateFinalValidator;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationControlRepository;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationNonceProvider;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationKeyReferenceStore;
import com.projectalice.backend.memory.infrastructure.relation.RelationKeyReferenceStore;
import com.projectalice.backend.memory.infrastructure.relation.RelationNonceProvider;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationReviewPublishRequestBuilder;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationReviewPublishExecutor;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationReviewStageStore;
import com.projectalice.backend.memory.infrastructure.relation.DynamoDbRelationReviewPublishActionFactory;
import com.projectalice.backend.memory.infrastructure.relation.EnvironmentRelationStateKeyProvider;
import com.projectalice.backend.memory.infrastructure.relation.EnvironmentRelationTargetSetDigestKeyProvider;
import com.projectalice.backend.memory.infrastructure.relation.HmacRelationTargetSetDigester;
import com.projectalice.backend.memory.infrastructure.relation.JceRelationAead;
import com.projectalice.backend.memory.infrastructure.relation.RelationAead;
import com.projectalice.backend.memory.infrastructure.relation.RelationReviewPayloadCodec;
import com.projectalice.backend.memory.infrastructure.relation.RelationReviewPublicationService;
import com.projectalice.backend.memory.infrastructure.relation.RelationStateKeyProvider;
import com.projectalice.backend.memory.infrastructure.relation.RelationTargetSetDigestKeyProvider;
import com.projectalice.backend.memory.infrastructure.relation.RelationTargetSetDigester;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Spring wiring for the shared, purpose-aware Relation/current Posting search path. */
@Configuration(proxyBeanMethods = false)
public class RelationSearchConfiguration {
    @Bean DynamoDbRelationReviewPublishRequestBuilder relationReviewPublishRequestBuilder() {
        return new DynamoDbRelationReviewPublishRequestBuilder();
    }

    @Bean DynamoDbRelationReviewPublishExecutor relationReviewPublishExecutor(DynamoDbClient client,
            DynamoDbRelationReviewPublishRequestBuilder builder) {
        return new DynamoDbRelationReviewPublishExecutor(client, builder);
    }

    @Bean DynamoDbRelationControlRepository dynamoDbRelationControlRepository(
            DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table,
            Clock clock) {
        return new DynamoDbRelationControlRepository(client, table, clock);
    }

    @Bean RelationNonceProvider relationNonceProvider(DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table) {
        return new DynamoDbRelationNonceProvider(client, table);
    }

    @Bean RelationKeyReferenceStore relationKeyReferenceStore(
            DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table,
            Clock clock) {
        return new DynamoDbRelationKeyReferenceStore(client, table, clock);
    }

    /** Relation key bytes must be injected by the deployment secret manager, never hard-coded. */
    @Bean
    @ConditionalOnProperty(name = "ALICE_RELATION_STATE_WRITE_KEY_VERSION")
    RelationStateKeyProvider relationStateKeyProvider() {
        return new EnvironmentRelationStateKeyProvider();
    }

    /** Independent HMAC key: it must not be reused from the Relation AES key or idempotency keys. */
    @Bean
    @ConditionalOnProperty(name = "ALICE_RELATION_TARGET_SET_DIGEST_KEY_BASE64")
    RelationTargetSetDigestKeyProvider relationTargetSetDigestKeyProvider() {
        return new EnvironmentRelationTargetSetDigestKeyProvider();
    }

    @Bean
    @ConditionalOnBean({RelationStateKeyProvider.class, RelationNonceProvider.class})
    RelationAead relationAead(RelationStateKeyProvider keys, RelationNonceProvider nonces) {
        return new JceRelationAead(keys, nonces);
    }

    @Bean RelationReviewPayloadCodec relationReviewPayloadCodec() {
        return new RelationReviewPayloadCodec();
    }

    @Bean
    @ConditionalOnBean(RelationTargetSetDigestKeyProvider.class)
    RelationTargetSetDigester relationTargetSetDigester(RelationTargetSetDigestKeyProvider key) {
        return new HmacRelationTargetSetDigester(key);
    }

    @Bean
    @ConditionalOnBean(RelationTargetSetDigester.class)
    DynamoDbRelationReviewStageStore dynamoDbRelationReviewStageStore(DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table,
            RelationTargetSetDigester digester) {
        return new DynamoDbRelationReviewStageStore(client, table, digester);
    }

    @Bean
    @ConditionalOnBean({DynamoDbRelationControlRepository.class, DynamoDbRelationReviewStageStore.class})
    DynamoDbRelationReviewPublishActionFactory relationReviewPublishActionFactory(
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table,
            DynamoDbRelationControlRepository controls, DynamoDbRelationReviewStageStore stageStore, Clock clock) {
        return new DynamoDbRelationReviewPublishActionFactory(table, controls, stageStore, clock);
    }

    @Bean
    @ConditionalOnBean({RelationStateKeyProvider.class, RelationAead.class, RelationTargetSetDigester.class,
            RelationKeyReferenceStore.class, DynamoDbRelationReviewStageStore.class,
            DynamoDbRelationReviewPublishActionFactory.class, DynamoDbRelationReviewPublishExecutor.class})
    RelationReviewPublicationService relationReviewPublicationService(RelationStateKeyProvider keys,
            RelationAead aead, RelationReviewPayloadCodec codec, RelationTargetSetDigester digester,
            RelationKeyReferenceStore references, DynamoDbRelationReviewStageStore stageStore,
            DynamoDbRelationReviewPublishActionFactory actions, DynamoDbRelationReviewPublishRequestBuilder builder,
            DynamoDbRelationReviewPublishExecutor executor, Clock clock) {
        return new RelationReviewPublicationService(keys, aead, codec, digester, references,
                stageStore, actions, builder, executor, clock);
    }

    @Bean PostingCandidateAccumulator postingCandidateAccumulator() {
        return new PostingCandidateAccumulator();
    }

    @Bean DynamoDbPostingReader dynamoDbPostingReader(DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table) {
        return new DynamoDbPostingReader(client, table);
    }

    @Bean DynamoDbSearchEvidenceReader dynamoDbSearchEvidenceReader(DynamoDbClient client,
            @Value("${ALICE_MEMORY_DYNAMODB_TABLE_NAME:project-alice-memory-local}") String table) {
        return new DynamoDbSearchEvidenceReader(client, table);
    }

    @Bean SearchCandidateFinalValidator searchCandidateFinalValidator() {
        return new SearchCandidateFinalValidator();
    }

    @Bean PostingSearchPipeline postingSearchPipeline(DynamoDbPostingReader postings,
            PostingCandidateAccumulator accumulator, PersonalMemoryRepository memories,
            DynamoDbSearchEvidenceReader evidence, SearchCandidateFinalValidator validator,
            CandidateGenerationLatencyMetrics latencyMetrics) {
        return new PostingSearchPipeline(postings, accumulator, memories, evidence, validator, latencyMetrics);
    }

    @Bean RelatedMemorySearchPort relatedMemorySearchPort(MemorySearchTermCapability capability,
            PostingSearchPipeline pipeline) {
        return new DynamoDbRelatedMemorySearchAdapter(capability, pipeline);
    }

    @Bean MemoryRelationDecisionUseCase memoryRelationDecisionUseCase(RelatedMemorySearchPort searchPort) {
        return new MemoryRelationDecisionService(searchPort);
    }
}
