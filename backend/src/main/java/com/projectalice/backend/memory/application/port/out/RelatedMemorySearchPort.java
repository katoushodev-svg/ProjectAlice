package com.projectalice.backend.memory.application.port.out;

import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.domain.MemoryContent;

/** Searches current, authoritative memories for possible relations to a new candidate. */
@FunctionalInterface
public interface RelatedMemorySearchPort {
    RelatedMemorySearchResult findCandidates(MemoryContent candidate);
}
