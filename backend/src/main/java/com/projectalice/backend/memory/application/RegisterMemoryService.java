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
    private final MemoryRelationDecisionUseCase relationDecision;
    private final RelationReviewDraftFactory reviewDraftFactory;

    public RegisterMemoryService(PersonalMemoryRepository personalMemoryRepository, Clock clock,
            MemoryRelationDecisionUseCase relationDecision) {
        this.personalMemoryRepository = Objects.requireNonNull(personalMemoryRepository, "personalMemoryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.relationDecision = Objects.requireNonNull(relationDecision, "relationDecision must not be null");
        this.reviewDraftFactory = new RelationReviewDraftFactory(personalMemoryRepository);
    }

    @Override
    public RegisterMemoryResult execute(RegisterMemoryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        MemoryRelationDecision decision = relationDecision.evaluate(command.content());
        switch (decision.status()) {
            case NO_MATCH -> { }
            case EXACT_DUPLICATE -> { return RegisterMemoryResult.relation(
                    RegisterMemoryResult.Status.NO_CHANGE, decision.candidateCount()); }
            case REVIEW_REQUIRED -> {
                // Validate the current target snapshots, but never report a Review outcome until
                // encryption, Binding ownership and the atomic publish transaction are available.
                reviewDraftFactory.create(decision, command);
                throw new RelationReviewPublicationUnavailableException();
            }
            case SEARCH_INCOMPLETE -> { return RegisterMemoryResult.relation(
                    RegisterMemoryResult.Status.RELATION_SEARCH_INCOMPLETE, decision.candidateCount()); }
            case SEARCH_UNAVAILABLE -> { return RegisterMemoryResult.relation(
                    RegisterMemoryResult.Status.RELATION_SEARCH_UNAVAILABLE, decision.candidateCount()); }
        }
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(PROJECT_TIMEZONE);
        PersonalMemory memory = PersonalMemory.create(
                command.content(), command.category(), CaptureType.EXPLICIT,
                command.sensitivityLevel(), now);
        personalMemoryRepository.save(memory);
        return RegisterMemoryResult.success(memory);
    }

}
