package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.application.port.TokenEstimator;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FindRelevantMemoriesServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    private final FakeRepository repository = new FakeRepository();
    private final FakeSearchPort searchPort = new FakeSearchPort();
    private final List<String> countedPayloads = new ArrayList<>();
    private Function<String, TokenEstimationResult> estimation =
            payload -> {
                countedPayloads.add(payload);
                return new TokenEstimationResult.Estimated(10);
            };
    private final TokenEstimator tokenEstimator = payload -> estimation.apply(payload);
    private final MemoryContextPayloadSerializer serializer = new MemoryContextPayloadSerializer();
    private final FindRelevantMemoriesService service =
            new FindRelevantMemoriesService(searchPort, repository, tokenEstimator, serializer);

    @Test
    void queryRequiresCurrentUserContent() {
        assertThrows(NullPointerException.class, () -> query(MemoryRetrievalPurpose.ANSWER_CURRENT, null));
        assertThrows(IllegalArgumentException.class, () -> query(MemoryRetrievalPurpose.ANSWER_CURRENT, " "));
    }

    @Test
    void callsSearchPortWithQuery() {
        MemoryRetrievalQuery query = query(MemoryRetrievalPurpose.ANSWER_CURRENT, "What do I use?");

        service.execute(query);

        assertEquals(List.of(query), searchPort.received);
    }

    @Test
    void hydratesAuthoritativeMemoryByCandidateId() {
        PersonalMemory memory = repository.add(memory("Uses Java.", MemoryCategory.ENGINEERING));
        searchPort.candidates = List.of(new MemorySearchCandidate(
                memory.memoryId(), memory.version(), Set.of(MemorySearchSignal.KEYWORD_MATCH)));

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "java"));

        assertEquals(List.of(memory.memoryId()), repository.requestedIds);
        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE, result.hydrationStatus());
        MemoryContextItem item = result.items().get(0);
        assertEquals("Uses Java.", item.content());
        assertEquals(MemoryTemporalRole.CURRENT, item.temporalRole());
        assertEquals(Set.of(MemoryRelevanceReasonCode.SEARCH_CANDIDATE), item.relevanceReasonCodes());
    }

    @Test
    void doesNotUseSearchCandidateContent() {
        // The candidate type has no content member at all.
        Set<String> names = componentNames(MemorySearchCandidate.class);
        assertFalse(names.contains("content"));
    }

    @Test
    void excludesMemoryMissingFromRepository() {
        searchPort.candidates = List.of(new MemorySearchCandidate(MemoryId.generate(), 1, Set.of()));

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertTrue(result.items().isEmpty());
        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE, result.hydrationStatus());
    }

    @Test
    void completeEmptySearchIsAValidEmptyResultWithoutHydrationOrEstimation() {
        searchPort.candidates = List.of();

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "no match"));

        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE, result.hydrationStatus());
        assertTrue(result.items().isEmpty());
        assertEquals("", result.payload());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED, result.estimationStatus());
        assertEquals(1, searchPort.received.size());
        assertTrue(countedPayloads.isEmpty());
    }

    @Test
    void partialSearchWithVerifiedCandidatesPreservesSearchPartiality() {
        PersonalMemory memory = repository.add(memory("Verified partial candidate.", MemoryCategory.PROJECT));
        searchPort.completeness = SearchCompleteness.PARTIAL;
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "partial"));

        assertEquals(SearchCompleteness.PARTIAL, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE, result.hydrationStatus());
        assertEquals(List.of("Verified partial candidate."), result.items().stream().map(MemoryContextItem::content).toList());
    }

    @Test
    void partialSearchWithNoVerifiedCandidatesDoesNotBecomeCompleteEmptySearch() {
        searchPort.completeness = SearchCompleteness.PARTIAL;
        searchPort.candidates = List.of();

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "partial with no verified hits"));

        assertEquals(SearchCompleteness.PARTIAL, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.COMPLETE, result.hydrationStatus());
        assertTrue(result.items().isEmpty());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED, result.estimationStatus());
    }

    @Test
    void unavailableSearchIsNotTreatedAsSuccessfulEmptySearch() {
        searchPort.completeness = SearchCompleteness.UNAVAILABLE;
        searchPort.candidates = List.of();

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "search unavailable"));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.NOT_ATTEMPTED, result.hydrationStatus());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED, result.estimationStatus());
        assertTrue(result.items().isEmpty());
        assertTrue(repository.requestedIds.isEmpty());
        assertTrue(countedPayloads.isEmpty());
    }

    @Test
    void reflectsAuthoritativeVersionAndMarksStaleCandidate() {
        PersonalMemory memory = repository.add(memory("Old.", MemoryCategory.PROFILE));
        memory.update(new MemoryContent("New."), MemoryCategory.PROFILE,
                SensitivityLevel.NORMAL, MemoryState.ACTIVE, NOW.plusMinutes(1));
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));

        MemoryContextItem item = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"))
                .items().get(0);

        assertEquals(2, item.memoryVersion());
        assertEquals("New.", item.content());
        assertTrue(item.relevanceReasonCodes().contains(MemoryRelevanceReasonCode.SEARCH_VERSION_STALE));
    }

    @Test
    void reflectsCategoryStateAndSensitivityFromAuthoritativeMemory() {
        PersonalMemory memory = repository.add(PersonalMemory.create(
                new MemoryContent("Lives in Tokyo."), MemoryCategory.PLACE,
                CaptureType.EXPLICIT, SensitivityLevel.SENSITIVE, NOW));
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));

        MemoryContextItem item = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"))
                .items().get(0);

        assertEquals(MemoryCategory.PLACE, item.category());
        assertEquals(MemoryState.ACTIVE, item.state());
        assertEquals(SensitivityLevel.SENSITIVE, item.sensitivityLevel());
    }

    @Test
    void resolvedMemoryIsNotEligibleForCurrentAnswer() {
        PersonalMemory memory = repository.add(memory("Was using Ruby.", MemoryCategory.ENGINEERING));
        memory.update(memory.content(), memory.category(), memory.sensitivityLevel(),
                MemoryState.RESOLVED, NOW.plusMinutes(1));
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 2, Set.of()));

        assertTrue(service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x")).items().isEmpty());
    }

    @Test
    void resolvedMemoryIsEligibleForHistoricalAnswer() {
        PersonalMemory memory = repository.add(memory("Was using Ruby.", MemoryCategory.ENGINEERING));
        memory.update(memory.content(), memory.category(), memory.sensitivityLevel(),
                MemoryState.RESOLVED, NOW.plusMinutes(1));
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 2, Set.of()));

        MemoryContextItem item = service.execute(query(MemoryRetrievalPurpose.ANSWER_HISTORICAL, "x"))
                .items().get(0);

        assertEquals(MemoryTemporalRole.HISTORICAL, item.temporalRole());
        assertEquals(MemoryState.RESOLVED, item.state());
    }

    @Test
    void activeMemoryIsNotConvertedToHistoricalContext() {
        PersonalMemory memory = repository.add(memory("Uses Java.", MemoryCategory.ENGINEERING));
        searchPort.candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));

        assertTrue(service.execute(query(MemoryRetrievalPurpose.ANSWER_HISTORICAL, "x")).items().isEmpty());
    }

    @Test
    void searchFailureYieldsUnavailableWithoutMemory() {
        searchPort.failure = new IllegalStateException("search down");

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(SearchCompleteness.UNAVAILABLE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.NOT_ATTEMPTED, result.hydrationStatus());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void oneHydrationFailureDoesNotAffectOtherCandidates() {
        PersonalMemory broken = repository.add(memory("Broken.", MemoryCategory.PROJECT));
        PersonalMemory healthy = repository.add(memory("Healthy.", MemoryCategory.PROJECT));
        repository.failingIds.add(broken.memoryId());
        searchPort.candidates = List.of(
                new MemorySearchCandidate(broken.memoryId(), 1, Set.of()),
                new MemorySearchCandidate(healthy.memoryId(), 1, Set.of()));

        FindRelevantMemoriesResult result = service.execute(
                query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.PARTIAL, result.hydrationStatus());
        assertEquals(1, result.items().size());
        assertEquals("Healthy.", result.items().get(0).content());
    }

    @Test
    void doesNotSelectFirstFortyBeforeRankingIsAvailable() {
        List<MemorySearchCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            PersonalMemory memory = repository.add(memory("Memory " + i, MemoryCategory.PROJECT));
            candidates.add(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));
        }
        searchPort.candidates = candidates;

        FindRelevantMemoriesResult result = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(SearchCompleteness.COMPLETE, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.NOT_ATTEMPTED, result.hydrationStatus());
        assertTrue(result.items().isEmpty());
        assertEquals("", result.payload());
        assertTrue(repository.requestedIds.isEmpty());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.NOT_ATTEMPTED, result.estimationStatus());
    }

    @Test
    void limitsFinalItemsWithoutTruncatingSelectedMemoryContent() {
        List<MemorySearchCandidate> candidates = new ArrayList<>();
        List<String> content = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String value = "Complete memory content " + i;
            content.add(value);
            PersonalMemory memory = repository.add(memory(value, MemoryCategory.PROJECT));
            candidates.add(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));
        }
        searchPort.candidates = candidates;

        List<MemoryContextItem> selected = service.execute(
                        query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"))
                .items();

        assertEquals(MemoryContextBudget.DEFAULT_MAX_FINAL_ITEMS, selected.size());
        assertEquals(content.subList(0, MemoryContextBudget.DEFAULT_MAX_FINAL_ITEMS),
                selected.stream().map(MemoryContextItem::content).toList());
    }

    @Test
    void skipsItemsThatExceedRemainingUtf8BudgetAndContinuesWithoutTruncation() {
        PersonalMemory tooLarge = repository.add(memory("🌸🌸", MemoryCategory.PROJECT));
        PersonalMemory firstFit = repository.add(memory("é", MemoryCategory.PROJECT));
        PersonalMemory secondFit = repository.add(memory("ab", MemoryCategory.PROJECT));
        searchPort.candidates = List.of(
                new MemorySearchCandidate(tooLarge.memoryId(), 1, Set.of()),
                new MemorySearchCandidate(firstFit.memoryId(), 1, Set.of()),
                new MemorySearchCandidate(secondFit.memoryId(), 1, Set.of()));
        MemoryContextBudget budget = new MemoryContextBudget(40, 8, 1500, 4);

        List<MemoryContextItem> selected = service.execute(
                        query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x", budget))
                .items();

        assertEquals(List.of("é", "ab"), selected.stream().map(MemoryContextItem::content).toList());
    }

    @Test
    void appliesItemAndUtf8LimitsTogetherAfterStep44Order() {
        List<MemorySearchCandidate> candidates = new ArrayList<>();
        List<String> originalOrder = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String value = String.valueOf((char) ('a' + i));
            originalOrder.add(value);
            PersonalMemory memory = repository.add(memory(value, MemoryCategory.PROJECT));
            candidates.add(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));
        }
        searchPort.candidates = candidates;
        MemoryContextBudget budget = new MemoryContextBudget(4, 3, 1500, 3);

        FindRelevantMemoriesResult result = new FindRelevantMemoriesService(
                        searchPort,
                        repository,
                        (q, ranked) -> ranked,
                        (q, ranked) -> ranked,
                        (q, diversified) -> List.of(
                                diversified.get(2), diversified.get(0), diversified.get(1), diversified.get(3)),
                        tokenEstimator,
                        serializer)
                .execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x", budget));

        assertEquals(List.of(originalOrder.get(2), originalOrder.get(0), originalOrder.get(1)),
                result.items().stream().map(MemoryContextItem::content).toList());
    }

    @Test
    void estimatesCompleteCumulativePayloadAndContinuesAfterAnOversizedCandidate() {
        addCandidate("first");
        addCandidate("bulky");
        addCandidate("later");
        estimation = payload -> {
            countedPayloads.add(payload);
            return new TokenEstimationResult.Estimated(payload.contains("bulky") ? 1501 : 200);
        };

        FindRelevantMemoriesResult result = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(List.of("first", "later"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals(List.of("[\"first\"]", "[\"first\",\"bulky\"]", "[\"first\",\"later\"]"), countedPayloads);
        assertEquals(countedPayloads.get(2), result.payload());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.ESTIMATED, result.estimationStatus());
        assertEquals(200, result.estimatedTokenCount().orElseThrow());
    }

    @Test
    void acceptsPayloadExactlyAtEstimatedTokenLimit() {
        addCandidate("at-cap");
        estimation = payload -> new TokenEstimationResult.Estimated(1500);

        FindRelevantMemoriesResult result = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(List.of("at-cap"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals(1500, result.estimatedTokenCount().orElseThrow());
    }

    @Test
    void estimatorFailureDiscardsPartialTokenSelectionAndUsesOriginalDiversifiedCandidates() {
        addCandidate("first");
        addCandidate("second");
        addCandidate("third");
        estimation = payload -> {
            countedPayloads.add(payload);
            return countedPayloads.size() == 1
                    ? new TokenEstimationResult.Estimated(100)
                    : new TokenEstimationResult.Unavailable();
        };

        FindRelevantMemoriesResult result = service.execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x"));

        assertEquals(List.of("first", "second", "third"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.UNAVAILABLE, result.estimationStatus());
        assertTrue(result.estimatedTokenCount().isEmpty());
        assertEquals("[\"first\",\"second\",\"third\"]", result.payload());
    }

    @Test
    void estimatorRuntimeExceptionUsesByteAndItemFallback() {
        addCandidate("éé");
        addCandidate("ok");
        estimation = payload -> {
            throw new IllegalStateException("tokenizer unavailable");
        };

        FindRelevantMemoriesResult result = new FindRelevantMemoriesService(
                        searchPort, repository, tokenEstimator, serializer)
                .execute(query(MemoryRetrievalPurpose.ANSWER_CURRENT, "x", new MemoryContextBudget(40, 8, 1500, 3)));

        assertEquals(List.of("ok"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals("[\"ok\"]", result.payload());
        assertEquals(FindRelevantMemoriesResult.EstimationStatus.UNAVAILABLE, result.estimationStatus());
    }

    private void addCandidate(String content) {
        PersonalMemory memory = repository.add(memory(content, MemoryCategory.PROJECT));
        List<MemorySearchCandidate> updated = new ArrayList<>(searchPort.candidates);
        updated.add(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));
        searchPort.candidates = List.copyOf(updated);
    }

    @Test
    void applicationModelsDoNotExposeProviderOrStorageDetails() {
        Set<String> names = new HashSet<>();
        for (Class<?> type : List.of(MemoryContextItem.class, MemorySearchCandidate.class,
                MemoryRetrievalQuery.class, FindRelevantMemoriesResult.class)) {
            names.addAll(componentNames(type));
        }
        for (String name : names) {
            String lower = name.toLowerCase();
            assertFalse(lower.equals("pk") || lower.equals("sk") || lower.contains("score")
                    || lower.contains("embedding") || lower.contains("revision")
                    || lower.contains("fingerprint"), name);
        }
    }

    private static Set<String> componentNames(Class<?> recordType) {
        Set<String> names = new HashSet<>();
        for (RecordComponent c : recordType.getRecordComponents()) {
            names.add(c.getName());
        }
        return names;
    }

    private static MemoryRetrievalQuery query(MemoryRetrievalPurpose purpose, String content) {
        return query(purpose, content, MemoryContextBudget.baseline());
    }

    private static MemoryRetrievalQuery query(
            MemoryRetrievalPurpose purpose, String content, MemoryContextBudget budget) {
        return new MemoryRetrievalQuery(purpose, content, null, null, null, null,
                MemoryTemporalIntent.UNSPECIFIED, budget, "req-1");
    }

    private static PersonalMemory memory(String content, MemoryCategory category) {
        return PersonalMemory.create(new MemoryContent(content), category,
                CaptureType.EXPLICIT, SensitivityLevel.NORMAL, NOW);
    }

    private static final class FakeSearchPort implements AnswerMemorySearchPort {
        List<MemorySearchCandidate> candidates = List.of();
        SearchCompleteness completeness = SearchCompleteness.COMPLETE;
        RuntimeException failure;
        final List<MemoryRetrievalQuery> received = new ArrayList<>();

        @Override
        public AnswerMemorySearchResult findCandidates(MemoryRetrievalQuery query) {
            received.add(query);
            if (failure != null) {
                throw failure;
            }
            return new AnswerMemorySearchResult(completeness, candidates);
        }
    }

    private static final class FakeRepository implements PersonalMemoryRepository {
        final Map<MemoryId, PersonalMemory> store = new HashMap<>();
        final Set<MemoryId> failingIds = new HashSet<>();
        final List<MemoryId> requestedIds = new ArrayList<>();

        PersonalMemory add(PersonalMemory memory) {
            store.put(memory.memoryId(), memory);
            return memory;
        }

        @Override
        public void save(PersonalMemory memory) {
            store.put(memory.memoryId(), memory);
        }

        @Override
        public Optional<PersonalMemory> findById(MemoryId memoryId) {
            requestedIds.add(memoryId);
            if (failingIds.contains(memoryId)) {
                throw new IllegalStateException("storage failure");
            }
            return Optional.ofNullable(store.get(memoryId));
        }

        @Override
        public void update(PersonalMemory memory, long expectedVersion) {
            store.put(memory.memoryId(), memory);
        }
    }
}
