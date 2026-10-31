package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterMemoryServiceTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void registersExplicitMemoryAndDelegatesToRepository() {
        RecordingRepository repository = new RecordingRepository();
        RegisterMemoryService service = new RegisterMemoryService(
                repository, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of())));
        RegisterMemoryResult result = service.execute(new RegisterMemoryCommand(
                new MemoryContent("Javaが好き"), MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL));

        assertEquals(RegisterMemoryResult.Status.SUCCESS, result.status());
        assertNotNull(result.memory());
        assertEquals(result.memory(), repository.saved);
        assertEquals(CaptureType.EXPLICIT, result.memory().captureType());
        assertEquals(MemoryState.ACTIVE, result.memory().state());
        assertEquals(1, result.memory().version());
        assertEquals(FIXED_INSTANT.atOffset(ZoneOffset.ofHours(9)), result.memory().createdAt());
        assertEquals(FIXED_INSTANT.atOffset(ZoneOffset.ofHours(9)), result.memory().updatedAt());
    }

    @Test
    void idempotentRegistrationFailsClosedUntilBindingPersistenceExists() {
        RecordingRepository repository = new RecordingRepository();
        RegisterMemoryService service = new RegisterMemoryService(repository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of())));
        RegisterMemoryCommand command = command();
        assertThrows(RegisterIdempotencyUnavailableException.class,
                () -> service.execute("550e8400-e29b-41d4-a716-446655440000", command));
        assertEquals(null, repository.saved);
    }

    @Test
    void rejectsNullCommand() {
        RegisterMemoryService service = new RegisterMemoryService(
                new RecordingRepository(), Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of())));
        assertThrows(NullPointerException.class, () -> service.execute(null));
    }

    @Test
    void doesNotSaveWhenRelationSearchIsIncompleteOrUnavailable() {
        for (SearchCompleteness completeness : java.util.List.of(
                SearchCompleteness.PARTIAL, SearchCompleteness.UNAVAILABLE)) {
            RecordingRepository repository = new RecordingRepository();
            RegisterMemoryService service = new RegisterMemoryService(repository,
                    Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                            new RelatedMemorySearchResult(completeness, java.util.List.of())));

            RegisterMemoryResult result = service.execute(command());

            assertEquals(completeness == SearchCompleteness.PARTIAL
                            ? RegisterMemoryResult.Status.RELATION_SEARCH_INCOMPLETE
                            : RegisterMemoryResult.Status.RELATION_SEARCH_UNAVAILABLE,
                    result.status());
            assertEquals(null, repository.saved);
        }
    }

    @Test
    void exactDuplicateReturnsNoChangeWithoutSaving() {
        RecordingRepository repository = new RecordingRepository();
        PersonalMemory existing = PersonalMemory.create(new MemoryContent("Javaが好き"),
                MemoryCategory.PREFERENCE, CaptureType.EXPLICIT, SensitivityLevel.NORMAL,
                FIXED_INSTANT.atOffset(ZoneOffset.UTC));
        RegisterMemoryService service = new RegisterMemoryService(repository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of(
                                new RelatedMemorySearchResult.Candidate(existing.memoryId(), existing.version(),
                                        existing.content(), existing.category(), existing.captureType(),
                                        existing.sensitivityLevel(), existing.state())))));

        RegisterMemoryResult result = service.execute(command());

        assertEquals(RegisterMemoryResult.Status.NO_CHANGE, result.status());
        assertEquals(1, result.relationCandidateCount());
        assertEquals(null, repository.saved);
    }

    @Test
    void keywordRelatedButNonIdenticalCandidateRequiresReviewAndDoesNotSave() {
        PersonalMemory existing = PersonalMemory.create(new MemoryContent("Javaを仕事で使っている"),
                MemoryCategory.PREFERENCE, CaptureType.EXPLICIT, SensitivityLevel.NORMAL,
                FIXED_INSTANT.atOffset(ZoneOffset.UTC));
        RecordingRepository repository = new RecordingRepository() {
            @Override public Optional<PersonalMemory> findById(MemoryId id) {
                return existing.memoryId().equals(id) ? Optional.of(existing) : Optional.empty();
            }
        };
        RegisterMemoryService service = new RegisterMemoryService(repository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of(
                                new RelatedMemorySearchResult.Candidate(existing.memoryId(), existing.version(),
                                        existing.content(), existing.category(), existing.captureType(),
                                        existing.sensitivityLevel(), existing.state())))));

        assertThrows(RelationReviewPublicationUnavailableException.class, () -> service.execute(command()));
        assertEquals(null, repository.saved);
    }

    @Test
    void changedTargetVersionFailsClosedWithoutReviewDraft() {
        PersonalMemory searched = PersonalMemory.create(new MemoryContent("Javaを仕事で使っている"),
                MemoryCategory.PREFERENCE, CaptureType.EXPLICIT, SensitivityLevel.NORMAL,
                FIXED_INSTANT.atOffset(ZoneOffset.UTC));
        PersonalMemory changed = PersonalMemory.reconstitute(searched.memoryId(),
                new MemoryContent("別の内容"), searched.category(), searched.captureType(),
                searched.sensitivityLevel(), MemoryState.ACTIVE, searched.version() + 1,
                searched.createdAt(), searched.updatedAt().plusSeconds(1), null);
        RecordingRepository repository = new RecordingRepository() {
            @Override public Optional<PersonalMemory> findById(MemoryId id) {
                return searched.memoryId().equals(id) ? Optional.of(changed) : Optional.empty();
            }
        };
        RegisterMemoryService service = new RegisterMemoryService(repository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of(
                                new RelatedMemorySearchResult.Candidate(searched.memoryId(), searched.version(),
                                        searched.content(), searched.category(), searched.captureType(),
                                        searched.sensitivityLevel(), searched.state())))));

        assertThrows(RelationReviewCandidateChangedException.class, () -> service.execute(command()));
        assertEquals(null, repository.saved);
    }

    @Test
    void missingTargetCannotProduceAReviewDraft() {
        var candidate = new RelatedMemorySearchResult.Candidate(new MemoryId("00000000-0000-4000-8000-000000000001"),
                3, new MemoryContent("Javaを仕事で使っている"), MemoryCategory.PREFERENCE,
                CaptureType.EXPLICIT, SensitivityLevel.NORMAL, MemoryState.ACTIVE);
        RegisterMemoryService service = new RegisterMemoryService(new RecordingRepository(),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(ignored ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of(candidate))));

        assertThrows(RelationReviewCandidateChangedException.class, () -> service.execute(command()));
    }

    @Test
    void relationSearchExceptionIsNotTreatedAsNoMatch() {
        RecordingRepository repository = new RecordingRepository();
        RegisterMemoryService service = new RegisterMemoryService(repository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate -> {
                    throw new IllegalStateException("search unavailable");
                }));

        RegisterMemoryResult result = service.execute(command());

        assertEquals(RegisterMemoryResult.Status.RELATION_SEARCH_UNAVAILABLE, result.status());
        assertEquals(null, repository.saved);
    }

    @Test
    void persistenceFailureAfterAConfirmedNoMatchStillPropagates() {
        PersonalMemoryRepository failingRepository = new RecordingRepository() {
            @Override public void save(PersonalMemory memory) {
                throw new IllegalStateException("save failed");
            }
        };
        RegisterMemoryService service = new RegisterMemoryService(failingRepository,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MemoryRelationDecisionService(candidate ->
                        new RelatedMemorySearchResult(SearchCompleteness.COMPLETE, java.util.List.of())));

        assertThrows(IllegalStateException.class, () -> service.execute(command()));
    }

    private static RegisterMemoryCommand command() {
        return new RegisterMemoryCommand(new MemoryContent("Javaが好き"),
                MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL);
    }

    private static class RecordingRepository implements PersonalMemoryRepository {
        private PersonalMemory saved;
        @Override
        public void save(PersonalMemory memory) { this.saved = memory; }

        @Override
        public Optional<PersonalMemory> findById(MemoryId memoryId) { return Optional.empty(); }

        @Override
        public void update(PersonalMemory memory, long expectedVersion) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
