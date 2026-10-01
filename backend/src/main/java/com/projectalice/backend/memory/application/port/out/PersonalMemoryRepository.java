package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Optional;

public interface PersonalMemoryRepository {
    void save(PersonalMemory memory);

    Optional<PersonalMemory> findById(MemoryId memoryId);
}
