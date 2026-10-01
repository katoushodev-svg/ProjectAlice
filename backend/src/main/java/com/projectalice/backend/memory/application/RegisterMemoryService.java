package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class RegisterMemoryService implements RegisterMemoryUseCase {
    private static final ZoneOffset PROJECT_TIMEZONE = ZoneOffset.ofHours(9);
    private final PersonalMemoryRepository personalMemoryRepository;
    private final Clock clock;

    public RegisterMemoryService(PersonalMemoryRepository personalMemoryRepository, Clock clock) {
        this.personalMemoryRepository = Objects.requireNonNull(personalMemoryRepository, "personalMemoryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public RegisterMemoryResult execute(RegisterMemoryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(PROJECT_TIMEZONE);
        PersonalMemory memory = PersonalMemory.create(
                command.content(), command.category(), CaptureType.EXPLICIT,
                command.sensitivityLevel(), now);
        personalMemoryRepository.save(memory);
        return RegisterMemoryResult.success(memory);
    }
}
