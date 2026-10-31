package com.projectalice.backend.memory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.projectalice.backend.memory.application.MemoryRetrievalPurpose;
import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemoryContextBudget;
import com.projectalice.backend.memory.application.MemoryTemporalIntent;
import com.projectalice.backend.memory.application.RegisterMemoryCommand;
import com.projectalice.backend.memory.application.RegisterMemoryResult;
import com.projectalice.backend.memory.application.RegisterMemoryUseCase;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.TokenEstimationResult;
import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.infrastructure.search.CandidateGenerationLatencyMetrics;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbPostingReader;
import com.projectalice.backend.memory.infrastructure.search.PostingSearchPipeline;
import com.projectalice.backend.memory.infrastructure.search.DynamoDbSearchEvidenceReader;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

class RelationSearchConfigurationTest {
    private static final String DIGEST = "A".repeat(43);
    private static final AtomicBoolean FAIL_QUERY = new AtomicBoolean();

    @Test
    void springWiresRelationPipelineToSharedMetricsAndRecordsItsRealSearchOutcome() {
        FAIL_QUERY.set(false);
        new ApplicationContextRunner()
                .withUserConfiguration(TestDependencies.class)
                .withPropertyValues(
                        "ALICE_MEMORY_SEARCH_TERM_KEYS=1:" + java.util.Base64.getEncoder().encodeToString(
                                "synthetic-memory-search-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "ALICE_MEMORY_SEARCH_TERM_WRITE_VERSION=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var metrics = context.getBean(CandidateGenerationLatencyMetrics.class);
                    var pipeline = context.getBean(PostingSearchPipeline.class);
                    assertSame(metrics, pipeline.latencyMetrics());

                    var relationPort = context.getBean(com.projectalice.backend.memory.application.port.out.RelatedMemorySearchPort.class);
                    var relationPortResult = relationPort.findCandidates(new MemoryContent("札幌で暮らす"));
                    assertEquals(SearchCompleteness.COMPLETE, relationPortResult.completeness());
                    assertEquals(1, metrics.samples(CandidateGenerationLatencyMetrics.Purpose.RELATION).size());

                    var answerResult = context.getBean(AnswerMemorySearchPort.class).findCandidates(
                            new MemoryRetrievalQuery(MemoryRetrievalPurpose.ANSWER_CURRENT, "test query",
                                    List.of(), List.of(), List.of(), java.util.Set.of(),
                                    MemoryTemporalIntent.UNSPECIFIED, MemoryContextBudget.baseline(), "answer-test"));
                    assertEquals(SearchCompleteness.COMPLETE, answerResult.completeness());
                    assertEquals(1, metrics.samples(CandidateGenerationLatencyMetrics.Purpose.ANSWER).size());

                    var result = pipeline.search(MemoryRetrievalPurpose.RELATION_DECISION,
                            List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)),
                            new PostingSearchPipeline.CurrentValidationRequest("KW_V1", ignored -> true,
                                    ignored -> true));

                    assertEquals(SearchCompleteness.COMPLETE, result.completeness());
                    assertTrue(result.candidates().isEmpty());
                    var relation = metrics.samples(CandidateGenerationLatencyMetrics.Purpose.RELATION);
                    assertEquals(2, relation.size());
                    assertEquals(CandidateGenerationLatencyMetrics.Outcome.COMPLETE, relation.getFirst().outcome());
                    assertEquals(CandidateGenerationLatencyMetrics.Outcome.COMPLETE,
                            metrics.samples(CandidateGenerationLatencyMetrics.Purpose.ANSWER).getFirst().outcome());

                    FAIL_QUERY.set(true);
                    var unavailable = pipeline.search(MemoryRetrievalPurpose.RELATION_DECISION,
                            List.of(new DynamoDbPostingReader.QueryTerm(1, DIGEST, 0)),
                            new PostingSearchPipeline.CurrentValidationRequest("KW_V1", ignored -> true,
                                    ignored -> true));
                    assertEquals(SearchCompleteness.UNAVAILABLE, unavailable.completeness());
                    assertEquals(CandidateGenerationLatencyMetrics.Outcome.UNAVAILABLE,
                            metrics.samples(CandidateGenerationLatencyMetrics.Purpose.RELATION).getLast().outcome());

                    RegisterMemoryResult failedRegistration = context.getBean(RegisterMemoryUseCase.class)
                            .execute(new RegisterMemoryCommand(new MemoryContent("札幌で暮らす"),
                                    MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL));
                    assertEquals(RegisterMemoryResult.Status.RELATION_SEARCH_UNAVAILABLE,
                            failedRegistration.status());
                    assertEquals(null, context.getBean(TestRepository.class).saved);

                    FAIL_QUERY.set(false);
                    RegisterMemoryResult registration = context.getBean(RegisterMemoryUseCase.class)
                            .execute(new RegisterMemoryCommand(new MemoryContent("札幌で暮らす"),
                                    MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL));
                    assertEquals(RegisterMemoryResult.Status.SUCCESS, registration.status());
                    assertEquals(registration.memory(), context.getBean(TestRepository.class).saved);
                    assertEquals(CandidateGenerationLatencyMetrics.Outcome.COMPLETE,
                            metrics.samples(CandidateGenerationLatencyMetrics.Purpose.RELATION).getLast().outcome());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MemorySearchConfiguration.class)
    static class TestDependencies {
        @Bean DynamoDbClient dynamoDbClient() {
            return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                    new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                        if (method.getName().equals("query") && args[0] instanceof QueryRequest) {
                            if (FAIL_QUERY.get()) throw new IllegalStateException("test query failure");
                            return QueryResponse.builder().items(List.of()).build();
                        }
                        if (method.getName().equals("getItem") && args[0] instanceof GetItemRequest request) {
                            String pk = request.key().get("pk").s();
                            if (DynamoDbSearchEvidenceReader.RESET_POINT_PK.equals(pk)) {
                                return GetItemResponse.builder().item(Map.of(
                                        "itemType", s("MEMORY_RESET_POINT"),
                                        "schemaVersion", n("1"),
                                        "generation", n("0"),
                                        "deletionBoundaryVersion", n("0"))).build();
                            }
                            return GetItemResponse.builder().item(Map.of()).build();
                        }
                        if (method.getName().equals("serviceName")) return "DynamoDb";
                        if (method.getName().equals("close")) return null;
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Bean TokenEstimator tokenEstimator() { return ignored -> new TokenEstimationResult.Estimated(0); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC); }
        @Bean @Primary TestRepository testRepository() { return new TestRepository(); }
    }

    static final class TestRepository implements PersonalMemoryRepository {
        private PersonalMemory saved;
        @Override public void save(PersonalMemory memory) { saved = memory; }
        @Override public Optional<PersonalMemory> findById(MemoryId id) { return Optional.empty(); }
        @Override public void update(PersonalMemory memory, long expectedVersion) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
