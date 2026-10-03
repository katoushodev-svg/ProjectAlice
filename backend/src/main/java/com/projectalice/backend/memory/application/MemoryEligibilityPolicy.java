package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Objects;
import java.util.Optional;

public final class MemoryEligibilityPolicy {

    public Optional<MemoryTemporalRole> evaluate(
            MemoryRetrievalPurpose purpose,
            MemoryRetrievalQuery query,
            MemorySearchCandidate candidate,
            PersonalMemory memory) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(memory, "memory must not be null");

        if (purpose == MemoryRetrievalPurpose.ANSWER_CURRENT && memory.state() == MemoryState.ACTIVE) {
            return Optional.of(MemoryTemporalRole.CURRENT);
        }
        if (purpose == MemoryRetrievalPurpose.ANSWER_HISTORICAL && memory.state() == MemoryState.RESOLVED) {
            return Optional.of(MemoryTemporalRole.HISTORICAL);
        }
        return Optional.empty();
    }
}