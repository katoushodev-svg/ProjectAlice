package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.application.port.out.MemoryPreferencesRepository;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UpdateMemoryPreferencesServiceTest {

    private static final OffsetDateTime EXPECTED_NOW =
            OffsetDateTime.parse("2026-10-17T12:00:00+09:00");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-10-17T03:00:00Z"),
                    ZoneOffset.UTC);

    @Test
    void executeDelegatesAllArgumentsUsingJstTimestamp() {
        RecordingRepository repository = new RecordingRepository();
        UpdateMemoryPreferencesService service =
                new UpdateMemoryPreferencesService(repository, FIXED_CLOCK);

        MemoryPreferences actual = service.execute(false, true, 7L);

        assertSame(repository.result, actual);
        assertEquals(Boolean.FALSE, repository.autoSaveEnabled);
        assertEquals(Boolean.TRUE, repository.answerUseEnabled);
        assertEquals(7L, repository.expectedVersion);
        assertEquals(EXPECTED_NOW, repository.changedAt);
        assertEquals(1, repository.updateCalls);
    }

    @Test
    void executeRejectsZeroExpectedVersionBeforeCallingRepository() {
        RecordingRepository repository = new RecordingRepository();
        UpdateMemoryPreferencesService service =
                new UpdateMemoryPreferencesService(repository, FIXED_CLOCK);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.execute(false, true, 0L));

        assertEquals(0, repository.updateCalls);
    }

    @Test
    void executeRejectsNegativeExpectedVersionBeforeCallingRepository() {
        RecordingRepository repository = new RecordingRepository();
        UpdateMemoryPreferencesService service =
                new UpdateMemoryPreferencesService(repository, FIXED_CLOCK);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.execute(false, true, -1L));

        assertEquals(0, repository.updateCalls);
    }

    @Test
    void executePropagatesRepositoryFailure() {
        RecordingRepository repository = new RecordingRepository();
        IllegalStateException failure =
                new IllegalStateException("preferences unavailable");
        repository.updateFailure = failure;

        UpdateMemoryPreferencesService service =
                new UpdateMemoryPreferencesService(repository, FIXED_CLOCK);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> service.execute(false, true, 1L));

        assertSame(failure, actual);
        assertEquals(1, repository.updateCalls);
    }

    private static final class RecordingRepository
            implements MemoryPreferencesRepository {

        private final MemoryPreferences result =
                MemoryPreferences.create(EXPECTED_NOW);

        private Boolean autoSaveEnabled;
        private Boolean answerUseEnabled;
        private long expectedVersion;
        private OffsetDateTime changedAt;
        private int updateCalls;
        private IllegalStateException updateFailure;

        @Override
        public MemoryPreferences getOrInitialize(OffsetDateTime initializedAt) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public MemoryPreferences update(
                boolean autoSaveEnabled,
                boolean answerUseEnabled,
                long expectedVersion,
                OffsetDateTime changedAt) {

            this.autoSaveEnabled = autoSaveEnabled;
            this.answerUseEnabled = answerUseEnabled;
            this.expectedVersion = expectedVersion;
            this.changedAt = changedAt;
            this.updateCalls++;

            if (updateFailure != null) {
                throw updateFailure;
            }

            return result;
        }
    }
}
