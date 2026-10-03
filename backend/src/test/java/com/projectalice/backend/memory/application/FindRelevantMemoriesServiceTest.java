package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.port.out.AnswerMemorySearchPort;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
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
import org.junit.jupiter.api.Test;

class FindRelevantMemoriesServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    private final FakeRepository repository = new FakeRepository();
    private final FakeSearchPort searchPort = new FakeSearchPort();
    private final FindRelevantMemoriesService service = new FindRelevantMemoriesService(searchPort, repository);

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
        assertEquals(FindRelevantMemoriesResult.Status.COMPLETE, result.status());
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
        assertEquals(FindRelevantMemoriesResult.Status.COMPLETE, result.status());
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

        assertEquals(FindRelevantMemoriesResult.Status.UNAVAILABLE, result.status());
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

        assertEquals(FindRelevantMemoriesResult.Status.PARTIAL, result.status());
        assertEquals(1, result.items().size());
        assertEquals("Healthy.", result.items().get(0).content());
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
        return new MemoryRetrievalQuery(purpose, content, null, null, null, null,
                MemoryTemporalIntent.UNSPECIFIED, MemoryContextBudget.baseline(), "req-1");
    }

    private static PersonalMemory memory(String content, MemoryCategory category) {
        return PersonalMemory.create(new MemoryContent(content), category,
                CaptureType.EXPLICIT, SensitivityLevel.NORMAL, NOW);
    }

    private static final class FakeSearchPort implements AnswerMemorySearchPort {
        List<MemorySearchCandidate> candidates = List.of();
        RuntimeException failure;
        final List<MemoryRetrievalQuery> received = new ArrayList<>();

        @Override
        public List<MemorySearchCandidate> findCandidates(MemoryRetrievalQuery query) {
            received.add(query);
            if (failure != null) {
                throw failure;
            }
            return candidates;
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
