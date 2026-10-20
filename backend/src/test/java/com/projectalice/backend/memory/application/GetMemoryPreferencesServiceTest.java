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

class GetMemoryPreferencesServiceTest {

    private static final OffsetDateTime EXPECTED_NOW =
            OffsetDateTime.parse("2026-10-17T12:00:00+09:00");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-10-17T03:00:00Z"),
                    ZoneOffset.UTC);

    @Test
    void executeDelegatesToRepositoryUsingJstTimestamp() {
        RecordingRepository repository = new RecordingRepository();
        GetMemoryPreferencesService service =
                new GetMemoryPreferencesService(repository, FIXED_CLOCK);

        MemoryPreferences actual = service.execute();

        assertSame(repository.result, actual);
        assertEquals(EXPECTED_NOW, repository.initializedAt);
        assertEquals(1, repository.getCalls);
    }

    @Test
    void executePropagatesRepositoryFailure() {
        RecordingRepository repository = new RecordingRepository();
        IllegalStateException failure =
                new IllegalStateException("preferences unavailable");
        repository.getFailure = failure;

        GetMemoryPreferencesService service =
                new GetMemoryPreferencesService(repository, FIXED_CLOCK);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                service::execute);

        assertSame(failure, actual);
        assertEquals(1, repository.getCalls);
    }

    private static final class RecordingRepository
            implements MemoryPreferencesRepository {

        private final MemoryPreferences result =
                MemoryPreferences.create(EXPECTED_NOW);

        private OffsetDateTime initializedAt;
        private int getCalls;
        private IllegalStateException getFailure;

        @Override
        public MemoryPreferences getOrInitialize(OffsetDateTime initializedAt) {
            this.initializedAt = initializedAt;
            this.getCalls++;

            if (getFailure != null) {
                throw getFailure;
            }

            return result;
        }

        @Override
        public MemoryPreferences update(
                boolean autoSaveEnabled,
                boolean answerUseEnabled,
                long expectedVersion,
                OffsetDateTime changedAt) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }
}
