package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.domain.PersonalMemory;

public interface PersonalMemoryRepository {
    void save(PersonalMemory memory);
}
