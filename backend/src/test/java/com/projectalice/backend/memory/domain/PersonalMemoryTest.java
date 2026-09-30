package com.projectalice.backend.memory.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PersonalMemoryTest {

    private static final OffsetDateTime CREATED =
            OffsetDateTime.of(2026, 9, 30, 21, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void createGeneratesActiveMemoryWithVersionOne() {
        PersonalMemory memory = PersonalMemory.create(
                new MemoryContent("Javaが好き"),
                MemoryCategory.PREFERENCE,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                CREATED);

        assertNotNull(memory.memoryId());
        assertEquals(MemoryState.ACTIVE, memory.state());
        assertEquals(1, memory.version());
        assertEquals(ZoneOffset.ofHours(9), memory.createdAt().getOffset());
        assertEquals(ZoneOffset.ofHours(9), memory.updatedAt().getOffset());
        assertNull(memory.confirmedAt());
    }

    @Test
    void updateKeepsIdentityAndIncrementsVersion() {
        PersonalMemory memory = PersonalMemory.create(
                new MemoryContent("Javaが好き"),
                MemoryCategory.PREFERENCE,
                CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL,
                CREATED);

        MemoryId originalId = memory.memoryId();
        OffsetDateTime changedAt = CREATED.plusHours(1);

        memory.update(
                new MemoryContent("JavaとGoが好き"),
                MemoryCategory.ENGINEERING,
                SensitivityLevel.NORMAL,
                MemoryState.ACTIVE,
                changedAt);

        assertEquals(originalId, memory.memoryId());
        assertEquals("JavaとGoが好き", memory.content().value());
        assertEquals(MemoryCategory.ENGINEERING, memory.category());
        assertEquals(2, memory.version());
        assertEquals(changedAt.withOffsetSameInstant(ZoneOffset.ofHours(9)), memory.updatedAt());
    }

    @Test
    void confirmDoesNotChangeVersion() {
        PersonalMemory memory = PersonalMemory.create(
                new MemoryContent("札幌が好き"),
                MemoryCategory.PLACE,
                CaptureType.AUTOMATIC,
                SensitivityLevel.NORMAL,
                CREATED);

        memory.confirm(CREATED.plusMinutes(10));

        assertEquals(1, memory.version());
        assertNotNull(memory.confirmedAt());
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThrows(IllegalArgumentException.class, () ->
                PersonalMemory.reconstitute(
                        MemoryId.generate(),
                        new MemoryContent("test"),
                        MemoryCategory.OTHER,
                        CaptureType.EXPLICIT,
                        SensitivityLevel.NORMAL,
                        MemoryState.ACTIVE,
                        1,
                        CREATED,
                        CREATED.minusMinutes(1),
                        null));
    }
}
