package com.projectalice.backend.memory.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Aggregate root representing the global Memory preferences.
 *
 * <p>This class intentionally has no Spring, AWS, DynamoDB, HTTP, or AI
 * dependencies.</p>
 *
 * <p>HTTP ETags and persistence-specific attributes are managed outside
 * the domain model.</p>
 */
public final class MemoryPreferences {

    private static final ZoneOffset PROJECT_TIMEZONE = ZoneOffset.ofHours(9);

    private final boolean autoSaveEnabled;
    private final boolean answerUseEnabled;
    private final long version;
    private final OffsetDateTime updatedAt;

    private MemoryPreferences(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long version,
            OffsetDateTime updatedAt) {

        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }

        this.autoSaveEnabled = autoSaveEnabled;
        this.answerUseEnabled = answerUseEnabled;
        this.version = version;
        this.updatedAt = normalizeJst(
                Objects.requireNonNull(updatedAt, "updatedAt must not be null"));
    }

    /**
     * Creates the initial preferences.
     *
     * <p>Both settings are enabled by default and the initial version is 1.</p>
     */
    public static MemoryPreferences create(OffsetDateTime initializedAt) {
        return new MemoryPreferences(
                true,
                true,
                1,
                initializedAt);
    }

    /**
     * Reconstitutes preferences loaded from persistent storage.
     */
    public static MemoryPreferences reconstitute(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long version,
            OffsetDateTime updatedAt) {

        return new MemoryPreferences(
                autoSaveEnabled,
                answerUseEnabled,
                version,
                updatedAt);
    }

    /**
     * Returns a new instance with the supplied settings and updated timestamp.
     *
     * <p>The caller is responsible for deciding whether a change is necessary
     * and for performing the corresponding persistence operation.</p>
     */
    public MemoryPreferences change(
            boolean newAutoSaveEnabled,
            boolean newAnswerUseEnabled,
            OffsetDateTime changedAt) {

        OffsetDateTime normalizedChangedAt = normalizeJst(
                Objects.requireNonNull(changedAt, "changedAt must not be null"));

        if (normalizedChangedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before updatedAt");
        }

        if (version == Long.MAX_VALUE) {
            throw new IllegalStateException("version cannot be incremented beyond Long.MAX_VALUE");
        }

        return new MemoryPreferences(
                newAutoSaveEnabled,
                newAnswerUseEnabled,
                version + 1,
                normalizedChangedAt);
    }

    public boolean autoSaveEnabled() {
        return autoSaveEnabled;
    }

    public boolean answerUseEnabled() {
        return answerUseEnabled;
    }

    public long version() {
        return version;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private static OffsetDateTime normalizeJst(OffsetDateTime value) {
        return value.withOffsetSameInstant(PROJECT_TIMEZONE);
    }
}