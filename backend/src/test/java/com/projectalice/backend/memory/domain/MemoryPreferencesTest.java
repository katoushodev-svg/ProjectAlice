package com.projectalice.backend.memory.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MemoryPreferencesTest {

    @Test
    void createUsesEnabledDefaultsAndInitialVersion() {
        OffsetDateTime initializedAt =
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00");

        MemoryPreferences preferences =
                MemoryPreferences.create(initializedAt);

        assertTrue(preferences.autoSaveEnabled());
        assertTrue(preferences.answerUseEnabled());
        assertEquals(1, preferences.version());
        assertEquals(initializedAt, preferences.updatedAt());
        assertEquals(ZoneOffset.ofHours(9), preferences.updatedAt().getOffset());
    }

    @Test
    void reconstituteRestoresPersistedValues() {
        OffsetDateTime updatedAt =
                OffsetDateTime.parse("2026-08-30T11:00:00Z");

        MemoryPreferences preferences = MemoryPreferences.reconstitute(
                false,
                true,
                5,
                updatedAt);

        assertFalse(preferences.autoSaveEnabled());
        assertTrue(preferences.answerUseEnabled());
        assertEquals(5, preferences.version());
        assertEquals(
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00"),
                preferences.updatedAt());
    }

    @Test
    void changeCreatesNewInstanceAndIncrementsVersion() {
        OffsetDateTime initialTime =
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00");
        OffsetDateTime changedTime =
                OffsetDateTime.parse("2026-08-30T20:01:00+09:00");

        MemoryPreferences original =
                MemoryPreferences.create(initialTime);

        MemoryPreferences changed =
                original.change(false, true, changedTime);

        assertTrue(original.autoSaveEnabled());
        assertTrue(original.answerUseEnabled());
        assertEquals(1, original.version());
        assertEquals(initialTime, original.updatedAt());

        assertFalse(changed.autoSaveEnabled());
        assertTrue(changed.answerUseEnabled());
        assertEquals(2, changed.version());
        assertEquals(changedTime, changed.updatedAt());
    }

    @Test
    void changeNormalizesTimestampToJst() {
        MemoryPreferences original = MemoryPreferences.create(
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00"));

        MemoryPreferences changed = original.change(
                false,
                true,
                OffsetDateTime.parse("2026-08-30T11:01:00Z"));

        assertEquals(
                OffsetDateTime.parse("2026-08-30T20:01:00+09:00"),
                changed.updatedAt());
        assertEquals(ZoneOffset.ofHours(9), changed.updatedAt().getOffset());
    }

    @Test
    void changeRejectsTimestampBeforeCurrentUpdatedAt() {
        MemoryPreferences original = MemoryPreferences.create(
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00"));

        assertThrows(
                IllegalArgumentException.class,
                () -> original.change(
                        false,
                        true,
                        OffsetDateTime.parse("2026-08-30T19:59:59+09:00")));
    }

    @Test
    void changeRejectsNullTimestamp() {
        MemoryPreferences original = MemoryPreferences.create(
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00"));

        assertThrows(
                NullPointerException.class,
                () -> original.change(false, true, null));
    }

    @Test
    void createRejectsNullTimestamp() {
        assertThrows(
                NullPointerException.class,
                () -> MemoryPreferences.create(null));
    }

    @Test
    void reconstituteRejectsVersionBelowOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MemoryPreferences.reconstitute(
                        true,
                        true,
                        0,
                        OffsetDateTime.parse("2026-08-30T20:00:00+09:00")));
    }

    @Test
    void changeRejectsVersionOverflow() {
        MemoryPreferences preferences = MemoryPreferences.reconstitute(
                true,
                true,
                Long.MAX_VALUE,
                OffsetDateTime.parse("2026-08-30T20:00:00+09:00"));

        assertThrows(
                IllegalStateException.class,
                () -> preferences.change(
                        false,
                        true,
                        OffsetDateTime.parse("2026-08-30T20:01:00+09:00")));
    }
}