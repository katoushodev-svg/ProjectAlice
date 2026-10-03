package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryRelevanceRankingFoundationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 3, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    private final Map<MemoryId, PersonalMemory> store = new HashMap<>();
    private List<MemorySearchCandidate> candidates = List.of();

    private final AnswerMemorySearchPort searchPort = query -> candidates;
    private final PersonalMemoryRepository repository = new PersonalMemoryRepository() {
        @Override
        public void save(PersonalMemory memory) {
            store.put(memory.memoryId(), memory);
        }

        @Override
        public Optional<PersonalMemory> findById(MemoryId memoryId) {
            return Optional.ofNullable(store.get(memoryId));
        }

        @Override
        public void update(PersonalMemory memory, long expectedVersion) {
            store.put(memory.memoryId(), memory);
        }
    };

    @Test
    void rankingReceivesOnlyEligibleAuthoritativeCandidates() {
        PersonalMemory active = add("Uses Java.", MemoryCategory.ENGINEERING, SensitivityLevel.SENSITIVE);
        PersonalMemory resolved = add("Used Ruby.", MemoryCategory.ENGINEERING, SensitivityLevel.NORMAL);
        resolved.update(resolved.content(), resolved.category(), resolved.sensitivityLevel(),
                MemoryState.RESOLVED, NOW.plusMinutes(1));
        candidates = List.of(
                new MemorySearchCandidate(active.memoryId(), 1, Set.of(MemorySearchSignal.SEMANTIC_MATCH)),
                new MemorySearchCandidate(resolved.memoryId(), 2, Set.of()));
        List<MemoryRankingCandidate> received = new ArrayList<>();

        new FindRelevantMemoriesService(searchPort, repository, (q, c) -> {
            received.addAll(c);
            return c;
        }).execute(query());

        assertEquals(1, received.size());
        MemoryContextItem item = received.get(0).item();
        assertEquals("Uses Java.", item.content());
        assertEquals(MemoryCategory.ENGINEERING, item.category());
        assertEquals(SensitivityLevel.SENSITIVE, item.sensitivityLevel());
        assertEquals(Set.of(MemoryRelevanceSignal.SEMANTIC_RELEVANCE), received.get(0).signals());
    }

    @Test
    void staleCandidateVersionIsNotExcludedAndContentIsAuthoritative() {
        PersonalMemory memory = add("Old.", MemoryCategory.PROFILE, SensitivityLevel.NORMAL);
        memory.update(new MemoryContent("New."), MemoryCategory.PROFILE,
                SensitivityLevel.NORMAL, MemoryState.ACTIVE, NOW.plusMinutes(1));
        candidates = List.of(new MemorySearchCandidate(memory.memoryId(), 1, Set.of()));
        List<MemoryRankingCandidate> received = new ArrayList<>();

        new FindRelevantMemoriesService(searchPort, repository, (q, c) -> {
            received.addAll(c);
            return c;
        }).execute(query());

        assertEquals(1, received.size());
        assertEquals("New.", received.get(0).item().content());
        assertEquals(2, received.get(0).item().memoryVersion());
    }

    @Test
    void defaultPolicyKeepsInputOrderWithoutThresholdOrTieBreaker() {
        PersonalMemory a = add("A.", MemoryCategory.PROJECT, SensitivityLevel.NORMAL);
        PersonalMemory b = add("B.", MemoryCategory.PROJECT, SensitivityLevel.NORMAL);
        candidates = List.of(
                new MemorySearchCandidate(b.memoryId(), 1, Set.of()),
                new MemorySearchCandidate(a.memoryId(), 1, Set.of()));

        FindRelevantMemoriesResult result =
                new FindRelevantMemoriesService(searchPort, repository).execute(query());

        assertEquals(List.of("B.", "A."), result.items().stream().map(MemoryContextItem::content).toList());
    }

    @Test
    void rankingModelsCarryNoProviderScoreOrWeight() {
        for (Class<?> type : List.of(MemoryRankingCandidate.class, MemoryContextItem.class)) {
            for (RecordComponent c : type.getRecordComponents()) {
                String name = c.getName().toLowerCase();
                assertFalse(name.contains("score") || name.contains("weight") || name.contains("threshold")
                        || name.contains("embedding") || name.contains("distance") || name.contains("provider"),
                        name);
            }
        }
        assertTrue(MemoryRelevanceSignal.valueOf("REDUNDANCY_PENALTY") != null);
    }

    private PersonalMemory add(String content, MemoryCategory category, SensitivityLevel sensitivity) {
        PersonalMemory memory = PersonalMemory.create(
                new MemoryContent(content), category, CaptureType.EXPLICIT, sensitivity, NOW);
        store.put(memory.memoryId(), memory);
        return memory;
    }

    private static MemoryRetrievalQuery query() {
        return new MemoryRetrievalQuery(MemoryRetrievalPurpose.ANSWER_CURRENT, "x", null, null, null, null,
                MemoryTemporalIntent.UNSPECIFIED, MemoryContextBudget.baseline(), "req-1");
    }
}
