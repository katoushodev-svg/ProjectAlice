package com.projectalice.backend.memory.application.port.out;

/** Reads the authoritative reset generation used to fence answer-time Memory selections. */
public interface MemoryResetPointRepository {
    long currentGeneration();
}
