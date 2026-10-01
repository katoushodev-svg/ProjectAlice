package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Optional;

public interface PersonalMemoryRepository {
    void save(PersonalMemory memory);

    Optional<PersonalMemory> findById(MemoryId memoryId);

    /**
     * Applies a semantic update to an existing memory, atomically creating its
     * {@code MemoryRevision}. {@code memory.version()} must already equal
     * {@code expectedVersion + 1}, or an {@link IllegalArgumentException} is thrown.
     * Fails with {@link MemoryUpdateConflictException} when {@code expectedVersion}
     * no longer matches the persisted version.
     */
    void update(PersonalMemory memory, long expectedVersion);
}
