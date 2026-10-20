package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.MemoryPreferencesRepository;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class GetMemoryPreferencesService implements GetMemoryPreferencesUseCase {

    private static final ZoneOffset PROJECT_TIMEZONE = ZoneOffset.ofHours(9);

    private final MemoryPreferencesRepository repository;
    private final Clock clock;

    public GetMemoryPreferencesService(
            MemoryPreferencesRepository repository,
            Clock clock) {
        this.repository = Objects.requireNonNull(
                repository, "repository must not be null");
        this.clock = Objects.requireNonNull(
                clock, "clock must not be null");
    }

    @Override
    public MemoryPreferences execute() {
        OffsetDateTime now = OffsetDateTime.now(clock)
                .withOffsetSameInstant(PROJECT_TIMEZONE);

        return repository.getOrInitialize(now);
    }
}