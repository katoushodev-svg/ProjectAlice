package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.AnswerMemorySearchResult;

public interface AnswerMemorySearchPort {
    AnswerMemorySearchResult findCandidates(MemoryRetrievalQuery query);
}
