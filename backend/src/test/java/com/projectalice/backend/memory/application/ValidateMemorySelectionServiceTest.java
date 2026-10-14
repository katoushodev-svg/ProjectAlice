package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.port.out.MemoryResetPointRepository;
import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidateMemorySelectionServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    @Test
    void preservesSelectionOrderAndRebuildsPayloadFromAuthoritativeContent() {
        PersonalMemory first = memory("first"), second = memory("second");
        List<MemoryId> reads = new ArrayList<>();
        PersonalMemoryRepository memories = repository(List.of(first, second), reads);
        var service = new ValidateMemorySelectionService(() -> 4, memories,
                payload -> new TokenEstimationResult.Estimated(2), new MemoryContextPayloadSerializer());
        var reference = new MemorySelectionReference("opaque-ref", 4, SearchCompleteness.COMPLETE, List.of(
                new MemorySelectionReference.Entry(first.memoryId(), first.version()),
                new MemorySelectionReference.Entry(second.memoryId(), second.version())));

        var result = service.validate(reference, query());

        assertEquals(List.of(first.memoryId(), second.memoryId()), reads);
        assertEquals(List.of("first", "second"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals(new MemoryContextPayloadSerializer().serialize(result.items()), result.payload());
        assertTrue(result.selectionReference().isPresent());
    }

    @Test
    void resetChangeInvalidatesTheWholeSelectionWithoutReadingMemory() {
        PersonalMemory memory = memory("stale selection");
        List<MemoryId> reads = new ArrayList<>();
        var service = new ValidateMemorySelectionService(() -> 5, repository(List.of(memory), reads),
                payload -> new TokenEstimationResult.Estimated(1), new MemoryContextPayloadSerializer());
        var reference = new MemorySelectionReference("opaque-ref", 4, SearchCompleteness.COMPLETE,
                List.of(new MemorySelectionReference.Entry(memory.memoryId(), memory.version())));

        var result = service.validate(reference, query());

        assertTrue(result.items().isEmpty());
        assertTrue(result.payload().isEmpty());
        assertTrue(reads.isEmpty());
        assertTrue(result.selectionReference().isEmpty());
    }

    @Test
    void retrievalCapturesResetGenerationAndOnlyOrderedIdsAndVersions() {
        PersonalMemory memory = memory("selected");
        PersonalMemoryRepository repository = repository(List.of(memory), new ArrayList<>());
        FindRelevantMemoriesService service = new FindRelevantMemoriesService(
                query -> AnswerMemorySearchResult.complete(List.of(new MemorySearchCandidate(
                        memory.memoryId(), memory.version(), java.util.Set.of(MemorySearchSignal.KEYWORD_MATCH)))),
                repository, MemoryRelevanceRankingPolicy.preservingInputOrder(),
                MemoryDeduplicationPolicy.preservingCandidates(), MemoryDiversityPolicy.preservingCandidates(),
                payload -> new TokenEstimationResult.Estimated(1), new MemoryContextPayloadSerializer(), () -> 9);

        FindRelevantMemoriesResult result = service.execute(query());

        assertTrue(result.selectionReference().isPresent());
        MemorySelectionReference reference = result.selectionReference().orElseThrow();
        assertEquals(9, reference.resetGeneration());
        assertEquals(List.of(new MemorySelectionReference.Entry(memory.memoryId(), memory.version())), reference.entries());
    }

    @Test
    void excludesDeletedOrVersionChangedItemsAndKeepsSurvivorOrder() {
        PersonalMemory stale = memory("old");
        PersonalMemory current = memory("survivor");
        var memories = new PersonalMemoryRepository() {
            public void save(PersonalMemory ignored) {}
            public Optional<PersonalMemory> findById(MemoryId id) {
                if (id.equals(stale.memoryId())) return Optional.of(PersonalMemory.reconstitute(
                        stale.memoryId(), new MemoryContent("new version"), stale.category(), stale.captureType(),
                        stale.sensitivityLevel(), stale.state(), 2, stale.createdAt(), stale.updatedAt(), stale.confirmedAt()));
                return id.equals(current.memoryId()) ? Optional.of(current) : Optional.empty();
            }
            public void update(PersonalMemory ignored, long version) {}
        };
        var service = new ValidateMemorySelectionService(() -> 4, memories,
                payload -> new TokenEstimationResult.Estimated(1), new MemoryContextPayloadSerializer());
        var reference = new MemorySelectionReference("opaque-ref", 4, SearchCompleteness.PARTIAL, List.of(
                new MemorySelectionReference.Entry(stale.memoryId(), 1),
                new MemorySelectionReference.Entry(MemoryId.generate(), 1),
                new MemorySelectionReference.Entry(current.memoryId(), 1)));

        var result = service.validate(reference, query());

        assertEquals(List.of("survivor"), result.items().stream().map(MemoryContextItem::content).toList());
        assertEquals(SearchCompleteness.PARTIAL, result.searchCompleteness());
        assertEquals(FindRelevantMemoriesResult.HydrationStatus.PARTIAL, result.hydrationStatus());
    }

    @Test
    void excludesSensitiveMemoryWhenTheReferenceHasNoDirectNeedRationale() {
        PersonalMemory sensitive = PersonalMemory.create(new MemoryContent("sensitive"), MemoryCategory.PROFILE,
                CaptureType.EXPLICIT, SensitivityLevel.SENSITIVE, NOW);
        var service = new ValidateMemorySelectionService(() -> 4, repository(List.of(sensitive), new ArrayList<>()),
                payload -> new TokenEstimationResult.Estimated(1), new MemoryContextPayloadSerializer());
        var reference = new MemorySelectionReference("opaque-ref", 4, SearchCompleteness.COMPLETE,
                List.of(new MemorySelectionReference.Entry(sensitive.memoryId(), sensitive.version())));

        var result = service.validate(reference, query());

        assertTrue(result.items().isEmpty());
        assertTrue(result.payload().isEmpty());
        assertTrue(result.selectionReference().isEmpty());
    }

    @Test
    void rejectsDuplicateOrOversizedReferences() {
        MemoryId id = MemoryId.generate();
        var entry = new MemorySelectionReference.Entry(id, 1);
        assertThrows(IllegalArgumentException.class, () -> new MemorySelectionReference(
                "ref", 1, SearchCompleteness.COMPLETE, List.of(entry, entry)));
        List<MemorySelectionReference.Entry> tooMany = java.util.stream.IntStream.range(0, 9)
                .mapToObj(ignored -> new MemorySelectionReference.Entry(MemoryId.generate(), 1)).toList();
        assertThrows(IllegalArgumentException.class, () -> new MemorySelectionReference(
                "ref", 1, SearchCompleteness.COMPLETE, tooMany));
    }

    private static PersonalMemoryRepository repository(List<PersonalMemory> values, List<MemoryId> reads) {
        return new PersonalMemoryRepository() {
            public void save(PersonalMemory ignored) {}
            public Optional<PersonalMemory> findById(MemoryId id) {
                reads.add(id);
                return values.stream().filter(value -> value.memoryId().equals(id)).findFirst();
            }
            public void update(PersonalMemory ignored, long version) {}
        };
    }

    private static PersonalMemory memory(String content) {
        return PersonalMemory.create(new MemoryContent(content), MemoryCategory.PROFILE,
                CaptureType.EXPLICIT, SensitivityLevel.NORMAL, NOW);
    }

    private static MemoryRetrievalQuery query() {
        return new MemoryRetrievalQuery(MemoryRetrievalPurpose.ANSWER_CURRENT, "question", List.of(), List.of(),
                List.of(), java.util.Set.of(), MemoryTemporalIntent.UNSPECIFIED, MemoryContextBudget.baseline(), "req");
    }
}
