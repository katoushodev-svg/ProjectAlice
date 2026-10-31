package com.projectalice.backend.memory.application;

/** Relation classifications accepted by the memory API contract. */
public enum RelationType {
    NONE,
    RELATED_INDEPENDENT,
    EQUIVALENT,
    COMPLEMENTARY_SAME_FACT,
    SUPERSEDES,
    CONFLICT,
    UNCERTAIN
}
