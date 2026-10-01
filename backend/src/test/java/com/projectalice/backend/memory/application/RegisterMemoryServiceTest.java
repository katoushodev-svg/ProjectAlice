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
                repository, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
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
    void rejectsNullCommand() {
        RegisterMemoryService service = new RegisterMemoryService(
                new RecordingRepository(), Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        assertThrows(NullPointerException.class, () -> service.execute(null));
    }

    private static final class RecordingRepository implements PersonalMemoryRepository {
        private PersonalMemory saved;
        @Override
        public void save(PersonalMemory memory) { this.saved = memory; }

        @Override
        public Optional<PersonalMemory> findById(MemoryId memoryId) { return Optional.empty(); }
    }
}
