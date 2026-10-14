package com.projectalice.backend.memory.application;

public interface ValidateMemorySelectionUseCase {
    FindRelevantMemoriesResult validate(MemorySelectionReference reference, MemoryRetrievalQuery query);
}
