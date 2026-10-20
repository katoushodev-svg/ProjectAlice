package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryPreferences;

public interface UpdateMemoryPreferencesUseCase {

    MemoryPreferences execute(
            boolean autoSaveEnabled,
            boolean answerUseEnabled,
            long expectedVersion);
}