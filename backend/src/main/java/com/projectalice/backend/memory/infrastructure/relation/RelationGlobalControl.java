package com.projectalice.backend.memory.infrastructure.relation;

/** Validated content-free global Active Review quota state. */
public record RelationGlobalControl(int activeCount, int maxActiveCount, long controlVersion) {
    public static final int REQUIRED_MAX_ACTIVE = 20;
    public RelationGlobalControl {
        if (activeCount < 0 || activeCount > REQUIRED_MAX_ACTIVE)
            throw new IllegalArgumentException("activeCount is outside the accepted range");
        if (maxActiveCount != REQUIRED_MAX_ACTIVE)
            throw new IllegalArgumentException("maxActiveCount must remain 20");
        if (controlVersion < 1) throw new IllegalArgumentException("controlVersion must be positive");
    }
}
