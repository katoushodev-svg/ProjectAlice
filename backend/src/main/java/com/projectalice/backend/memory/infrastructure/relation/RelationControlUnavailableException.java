package com.projectalice.backend.memory.infrastructure.relation;

/** Sanitized fail-closed error for malformed or unavailable Relation Control state. */
public final class RelationControlUnavailableException extends RuntimeException {
    public RelationControlUnavailableException() { super("Relation Control is unavailable"); }
}
