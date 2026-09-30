package com.projectalice.backend.memory.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Aggregate root representing one long-lived Personal Memory.
 *
 * <p>This class intentionally has no Spring, AWS, DynamoDB, HTTP, or AI
 * dependencies.</p>
 */
public final class PersonalMemory {

    private static final ZoneOffset PROJECT_TIMEZONE = ZoneOffset.ofHours(9);

    private final MemoryId memoryId;
    private MemoryContent content;
    private MemoryCategory category;
    private final CaptureType captureType;
    private SensitivityLevel sensitivityLevel;
    private MemoryState state;
    private long version;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime confirmedAt;

    private PersonalMemory(
            MemoryId memoryId,
            MemoryContent content,
            MemoryCategory category,
            CaptureType captureType,
            SensitivityLevel sensitivityLevel,
            MemoryState state,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime confirmedAt) {

        this.memoryId = Objects.requireNonNull(memoryId, "memoryId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.captureType = Objects.requireNonNull(captureType, "captureType must not be null");
        this.sensitivityLevel = Objects.requireNonNull(
                sensitivityLevel, "sensitivityLevel must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");

        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }

        this.createdAt = normalizeJst(Objects.requireNonNull(createdAt, "createdAt must not be null"));
        this.updatedAt = normalizeJst(Objects.requireNonNull(updatedAt, "updatedAt must not be null"));

        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }

        this.confirmedAt = confirmedAt == null ? null : normalizeJst(confirmedAt);
        if (this.confirmedAt != null && this.confirmedAt.isBefore(this.createdAt)) {
            throw new IllegalArgumentException("confirmedAt must not be before createdAt");
        }

        this.version = version;
    }

    public static PersonalMemory create(
            MemoryContent content,
            MemoryCategory category,
            CaptureType captureType,
            SensitivityLevel sensitivityLevel,
            OffsetDateTime createdAt) {

        OffsetDateTime normalizedCreatedAt = normalizeJst(
                Objects.requireNonNull(createdAt, "createdAt must not be null"));

        return new PersonalMemory(
                MemoryId.generate(),
                content,
                category,
                captureType,
                sensitivityLevel,
                MemoryState.ACTIVE,
                1,
                normalizedCreatedAt,
                normalizedCreatedAt,
                null);
    }

    public static PersonalMemory reconstitute(
            MemoryId memoryId,
            MemoryContent content,
            MemoryCategory category,
            CaptureType captureType,
            SensitivityLevel sensitivityLevel,
            MemoryState state,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime confirmedAt) {

        return new PersonalMemory(
                memoryId,
                content,
                category,
                captureType,
                sensitivityLevel,
                state,
                version,
                createdAt,
                updatedAt,
                confirmedAt);
    }

    public void update(
            MemoryContent newContent,
            MemoryCategory newCategory,
            SensitivityLevel newSensitivityLevel,
            MemoryState newState,
            OffsetDateTime changedAt) {

        OffsetDateTime normalizedChangedAt = normalizeJst(
                Objects.requireNonNull(changedAt, "changedAt must not be null"));

        if (normalizedChangedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before updatedAt");
        }

        this.content = Objects.requireNonNull(newContent, "content must not be null");
        this.category = Objects.requireNonNull(newCategory, "category must not be null");
        this.sensitivityLevel = Objects.requireNonNull(
                newSensitivityLevel, "sensitivityLevel must not be null");
        this.state = Objects.requireNonNull(newState, "state must not be null");
        this.updatedAt = normalizedChangedAt;
        this.version++;
    }

    public void confirm(OffsetDateTime confirmedAt) {
        OffsetDateTime normalizedConfirmedAt = normalizeJst(
                Objects.requireNonNull(confirmedAt, "confirmedAt must not be null"));

        if (normalizedConfirmedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("confirmedAt must not be before createdAt");
        }

        this.confirmedAt = normalizedConfirmedAt;
    }

    public MemoryId memoryId() {
        return memoryId;
    }

    public MemoryContent content() {
        return content;
    }

    public MemoryCategory category() {
        return category;
    }

    public CaptureType captureType() {
        return captureType;
    }

    public SensitivityLevel sensitivityLevel() {
        return sensitivityLevel;
    }

    public MemoryState state() {
        return state;
    }

    public long version() {
        return version;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime confirmedAt() {
        return confirmedAt;
    }

    private static OffsetDateTime normalizeJst(OffsetDateTime value) {
        return value.withOffsetSameInstant(PROJECT_TIMEZONE);
    }
}
