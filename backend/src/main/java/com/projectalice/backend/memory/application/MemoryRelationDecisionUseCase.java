package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryContent;

public interface MemoryRelationDecisionUseCase {
    MemoryRelationDecision evaluate(MemoryContent candidate);
}
