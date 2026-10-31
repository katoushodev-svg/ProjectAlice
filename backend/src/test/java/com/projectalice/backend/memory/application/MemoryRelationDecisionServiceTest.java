package com.projectalice.backend.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.domain.MemoryState;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryRelationDecisionServiceTest {
    private static final MemoryContent INPUT = new MemoryContent("札幌で暮らす");

    @Test
    void completeEmptySearchIsNone() {
        var result = service(SearchCompleteness.COMPLETE, List.of()).evaluate(INPUT);
        assertEquals(MemoryRelationDecision.Status.NO_MATCH, result.status());
        assertEquals(RelationType.NONE, result.relationType().orElseThrow());
    }

    @Test
    void exactContentIsEquivalent() {
        var result = service(SearchCompleteness.COMPLETE, List.of(candidate("札幌で暮らす"))).evaluate(INPUT);
        assertEquals(MemoryRelationDecision.Status.EXACT_DUPLICATE, result.status());
        assertEquals(RelationType.EQUIVALENT, result.relationType().orElseThrow());
    }

    @Test
    void nonExactCandidateRemainsUncertainWithoutSemanticEvidence() {
        var result = service(SearchCompleteness.COMPLETE, List.of(candidate("札幌へ引っ越す"))).evaluate(INPUT);
        assertEquals(MemoryRelationDecision.Status.REVIEW_REQUIRED, result.status());
        assertEquals(RelationType.UNCERTAIN, result.relationType().orElseThrow());
        assertEquals(1, result.candidates().size());
        assertEquals("札幌へ引っ越す", result.candidates().getFirst().content().value());
    }

    @Test
    void partialAndUnavailableResultsHaveNoRelationType() {
        for (SearchCompleteness completeness : List.of(SearchCompleteness.PARTIAL, SearchCompleteness.UNAVAILABLE)) {
            var result = service(completeness, List.of()).evaluate(INPUT);
            assertEquals(completeness == SearchCompleteness.PARTIAL
                    ? MemoryRelationDecision.Status.SEARCH_INCOMPLETE
                    : MemoryRelationDecision.Status.SEARCH_UNAVAILABLE, result.status());
            assertEquals(java.util.Optional.empty(), result.relationType());
        }
    }

    @Test
    void thrownSearchFailureIsUnavailableAndNotNone() {
        var result = new MemoryRelationDecisionService(ignored -> { throw new IllegalStateException(); })
                .evaluate(INPUT);
        assertEquals(MemoryRelationDecision.Status.SEARCH_UNAVAILABLE, result.status());
        assertEquals(java.util.Optional.empty(), result.relationType());
    }

    @Test
    void decisionRejectsInconsistentTypeAndSearchState() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryRelationDecision(
                MemoryRelationDecision.Status.REVIEW_REQUIRED, 1, java.util.Optional.of(RelationType.NONE)));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRelationDecision(
                MemoryRelationDecision.Status.SEARCH_INCOMPLETE, 0, java.util.Optional.of(RelationType.NONE)));
    }

    @Test
    void reviewClassificationContractAcceptsOnlyDefinedReviewTypes() {
        for (RelationType type : List.of(RelationType.COMPLEMENTARY_SAME_FACT, RelationType.SUPERSEDES,
                RelationType.CONFLICT, RelationType.UNCERTAIN)) {
            var decision = new MemoryRelationDecision(MemoryRelationDecision.Status.REVIEW_REQUIRED,
                    1, java.util.Optional.of(type));
            assertEquals(type, decision.relationType().orElseThrow());
        }
        for (RelationType type : List.of(RelationType.NONE, RelationType.RELATED_INDEPENDENT,
                RelationType.EQUIVALENT)) {
            assertThrows(IllegalArgumentException.class, () -> new MemoryRelationDecision(
                    MemoryRelationDecision.Status.REVIEW_REQUIRED, 1, java.util.Optional.of(type)));
        }
    }

    private static MemoryRelationDecisionService service(SearchCompleteness completeness,
            List<RelatedMemorySearchResult.Candidate> candidates) {
        return new MemoryRelationDecisionService(ignored -> new RelatedMemorySearchResult(completeness, candidates));
    }

    private static RelatedMemorySearchResult.Candidate candidate(String text) {
        return new RelatedMemorySearchResult.Candidate(new MemoryId("00000000-0000-4000-8000-000000000001"),
                3, new MemoryContent(text), MemoryCategory.PREFERENCE, CaptureType.EXPLICIT,
                SensitivityLevel.NORMAL, MemoryState.ACTIVE);
    }
}
