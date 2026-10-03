package com.projectalice.backend.memory.application;

public interface FindRelevantMemoriesUseCase {
    FindRelevantMemoriesResult execute(MemoryRetrievalQuery query);
}
