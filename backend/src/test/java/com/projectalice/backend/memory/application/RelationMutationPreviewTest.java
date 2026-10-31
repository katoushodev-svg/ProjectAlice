package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryState;
import org.junit.jupiter.api.Test;

class RelationMutationPreviewTest {
    private static final RegisterMemoryCommand PROPOSED = new RegisterMemoryCommand(
            new MemoryContent("札幌市内に住んでいる"), MemoryCategory.PLACE, SensitivityLevel.NORMAL);
    private static final RelatedMemorySearchResult.Candidate TARGET =
            new RelatedMemorySearchResult.Candidate(
                    new MemoryId("00000000-0000-4000-8000-000000000001"), 7,
                    new MemoryContent("北海道に住んでいる"), MemoryCategory.PLACE, CaptureType.EXPLICIT,
                    SensitivityLevel.NORMAL, MemoryState.ACTIVE);

    @Test
    void completeValidatedCandidateProducesVersionBoundPreview() {
        for (RelationType type : new RelationType[] {RelationType.COMPLEMENTARY_SAME_FACT,
                RelationType.SUPERSEDES, RelationType.CONFLICT, RelationType.UNCERTAIN}) {
            var preview = RelationMutationPreview.forRegister(SearchCompleteness.COMPLETE, type, TARGET, PROPOSED);
            assertEquals(RelationMutationPreview.Operation.UPDATE_TARGET, preview.operation());
            assertEquals(type, preview.relationType());
            assertEquals(TARGET.memoryId(), preview.targetMemoryId());
            assertEquals(7, preview.expectedTargetVersion());
            assertEquals(PROPOSED.content(), preview.proposedContent());
            assertEquals(PROPOSED.category(), preview.proposedCategory());
            assertEquals(PROPOSED.sensitivityLevel(), preview.proposedSensitivityLevel());
        }
    }

    @Test
    void incompleteSearchCannotProducePreview() {
        assertThrows(IllegalArgumentException.class, () -> RelationMutationPreview.forRegister(
                SearchCompleteness.PARTIAL, RelationType.UNCERTAIN, TARGET, PROPOSED));
        assertThrows(IllegalArgumentException.class, () -> RelationMutationPreview.forRegister(
                SearchCompleteness.UNAVAILABLE, RelationType.UNCERTAIN, TARGET, PROPOSED));
    }

    @Test
    void nonReviewTypesCannotProduceUpdatePreview() {
        for (RelationType type : new RelationType[] {RelationType.NONE, RelationType.RELATED_INDEPENDENT,
                RelationType.EQUIVALENT}) {
            assertThrows(IllegalArgumentException.class, () ->
                    RelationMutationPreview.forRegister(SearchCompleteness.COMPLETE, type, TARGET, PROPOSED));
        }
    }

    @Test
    void missingTargetOrCandidateFieldsAreRejected() {
        assertThrows(NullPointerException.class, () -> RelationMutationPreview.forRegister(
                SearchCompleteness.COMPLETE, RelationType.UNCERTAIN, null, PROPOSED));
        assertThrows(NullPointerException.class, () -> RelationMutationPreview.forRegister(
                SearchCompleteness.COMPLETE, RelationType.UNCERTAIN, TARGET, null));
        assertThrows(IllegalArgumentException.class, () -> new RelationMutationPreview(
                RelationMutationPreview.Operation.UPDATE_TARGET, RelationType.UNCERTAIN,
                TARGET.memoryId(), 0, PROPOSED.content(), PROPOSED.category(), PROPOSED.sensitivityLevel()));
    }
}
