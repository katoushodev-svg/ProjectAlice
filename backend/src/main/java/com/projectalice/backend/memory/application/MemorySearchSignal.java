package com.projectalice.backend.memory.application;

/** Provider-independent hint on why a search source proposed a candidate. */
public enum MemorySearchSignal {
    KEYWORD_MATCH,
    SEMANTIC_MATCH,
    ENTITY_MATCH,
    CATEGORY_MATCH
}
