package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.Objects;

public record RegisterMemoryCommand(
        MemoryContent content,
        MemoryCategory category,
        SensitivityLevel sensitivityLevel) {
    public RegisterMemoryCommand {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(sensitivityLevel, "sensitivityLevel must not be null");
    }
}
