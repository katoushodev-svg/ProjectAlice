package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.application.port.out.PersonalMemoryRepository;
import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.ArrayList;
import java.util.Objects;

/** Rechecks search snapshots against the repository's strongly consistent current read before review handoff. */
public final class RelationReviewDraftFactory {
    private final PersonalMemoryRepository memories;

    public RelationReviewDraftFactory(PersonalMemoryRepository memories) {
        this.memories = Objects.requireNonNull(memories, "memories must not be null");
    }

    public RelationReviewDraft create(MemoryRelationDecision decision, RegisterMemoryCommand proposed) {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(proposed, "proposed must not be null");
        if (decision.status() != MemoryRelationDecision.Status.REVIEW_REQUIRED
                || decision.relationType().isEmpty() || decision.candidates().isEmpty()
                || decision.candidates().size() != decision.candidateCount())
            throw new IllegalArgumentException("complete Review decision with all candidates is required");

        ArrayList<RelationReviewDraft.Target> targets = new ArrayList<>(decision.candidates().size());
        for (RelatedMemorySearchResult.Candidate searched : decision.candidates()) {
            PersonalMemory current = memories.findById(searched.memoryId()).orElseThrow(
                    RelationReviewCandidateChangedException::new);
            if (current.state() != searched.state() || current.state() != com.projectalice.backend.memory.domain.MemoryState.ACTIVE
                    || current.version() != searched.version()
                    || !current.content().equals(searched.content())
                    || current.category() != searched.category()
                    || current.captureType() != searched.captureType()
                    || current.sensitivityLevel() != searched.sensitivityLevel())
                throw new RelationReviewCandidateChangedException();
            RelatedMemorySearchResult.Candidate authoritative = new RelatedMemorySearchResult.Candidate(
                    current.memoryId(), current.version(), current.content(), current.category(),
                    current.captureType(), current.sensitivityLevel(), current.state());
            RelationMutationPreview preview = RelationMutationPreview.forRegister(
                    SearchCompleteness.COMPLETE, decision.relationType().orElseThrow(), authoritative, proposed);
            targets.add(new RelationReviewDraft.Target(authoritative, preview));
        }
        return new RelationReviewDraft(decision.relationType().orElseThrow(), proposed.content(),
                proposed.category(), proposed.sensitivityLevel(), targets);
    }
}
