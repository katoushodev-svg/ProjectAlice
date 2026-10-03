package com.projectalice.backend.memory.application;

/** Provider-neutral ranking signals; carries no weight, score or ordering. */
public enum MemoryRelevanceSignal {
    DIRECT_SUBJECT_ENTITY_MATCH,
    ATTRIBUTE_INTENT_MATCH,
    SCOPE_MATCH,
    SEMANTIC_RELEVANCE,
    CURRENTNESS,
    EXPLICIT_CONFIRMATION,
    SPECIFICITY,
    RECENCY,
    REDUNDANCY_PENALTY
}
