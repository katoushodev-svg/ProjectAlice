package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.time.OffsetDateTime;

/**
 * Persistence port for the global Memory preferences.
 *
 * <p>Implementations must preserve the initialization and concurrency
 * guarantees defined by the Memory Preferences design.</p>
 */
public interface MemoryPreferencesRepository {

    /**
     * Returns the current preferences.
     *
     * <p>If the preferences have not yet been initialized, the implementation
     * may initialize them only when the authoritative Feature State permits
     * first-time initialization.</p>
     *
     * @throws IllegalStateException if the stored configuration is missing,
     *         corrupt, or unavailable
     */
    MemoryPreferences getOrInitialize(OffsetDateTime initializedAt);

    /**
     * Updates preferences using optimistic concurrency control.
     *
     * <p>The expected version must match the currently persisted version.
     * Implementations must not write when the supplied settings are unchanged.</p>
     *
     * @throws MemoryPreferencesUpdateConflictException if the expected version
     *         no longer matches the persisted version
     * @throws IllegalStateException if the stored configuration is invalid
     *         or unavailable
     */
    MemoryPreferences update(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long expectedVersion,
            OffsetDateTime changedAt);
}