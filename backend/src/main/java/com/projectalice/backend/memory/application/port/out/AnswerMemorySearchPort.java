package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.application.MemoryRetrievalQuery;
import com.projectalice.backend.memory.application.MemorySearchCandidate;
import java.util.List;

public interface AnswerMemorySearchPort {
    List<MemorySearchCandidate> findCandidates(MemoryRetrievalQuery query);
}
