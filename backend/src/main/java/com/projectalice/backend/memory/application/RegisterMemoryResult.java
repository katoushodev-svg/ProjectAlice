package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Objects;

public record RegisterMemoryResult(Status status, PersonalMemory memory) {
    public enum Status { SUCCESS }
    public RegisterMemoryResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.SUCCESS) Objects.requireNonNull(memory, "memory must not be null for SUCCESS");
    }
    public static RegisterMemoryResult success(PersonalMemory memory) {
        return new RegisterMemoryResult(Status.SUCCESS, Objects.requireNonNull(memory));
    }
}
