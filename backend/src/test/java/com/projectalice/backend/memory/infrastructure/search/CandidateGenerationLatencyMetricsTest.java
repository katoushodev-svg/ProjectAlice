package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.application.SearchCompleteness;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CandidateGenerationLatencyMetricsTest {
    @Test
    void emptyAndInsufficientSamplesRemainUnmeasured() {
        CandidateGenerationLatencyMetrics metrics = new CandidateGenerationLatencyMetrics(() -> 0L);
        assertEquals(CandidateGenerationLatencyMetrics.Decision.UNMEASURED,
                metrics.summary(CandidateGenerationLatencyMetrics.Purpose.ANSWER).decision());
        metrics.measure(CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                () -> "ok", ignored -> SearchCompleteness.COMPLETE);
        var summary = metrics.summary(CandidateGenerationLatencyMetrics.Purpose.ANSWER);
        assertEquals(1, summary.sampleCount());
        assertEquals(CandidateGenerationLatencyMetrics.Decision.UNMEASURED, summary.decision());
        assertEquals(1, metrics.samples(CandidateGenerationLatencyMetrics.Purpose.ANSWER).size());
    }

    @Test
    void calculatesNearestRankP95AndSeparatesPurposes() {
        AtomicLong clock = new AtomicLong();
        CandidateGenerationLatencyMetrics metrics = new CandidateGenerationLatencyMetrics(
                () -> clock.getAndAdd(10_000_000L));
        for (int i = 0; i < CandidateGenerationLatencyMetrics.MIN_SAMPLES_FOR_P95; i++) {
            int value = i;
            metrics.measure(CandidateGenerationLatencyMetrics.Purpose.RELATION,
                    () -> value, ignored -> SearchCompleteness.COMPLETE);
        }

        var summary = metrics.summary(CandidateGenerationLatencyMetrics.Purpose.RELATION);
        assertEquals(20, summary.sampleCount());
        assertEquals(10_000_000L, summary.p95Nanos().orElseThrow());
        assertEquals(CandidateGenerationLatencyMetrics.Decision.PASS, summary.decision());
        assertEquals(0, metrics.summary(CandidateGenerationLatencyMetrics.Purpose.ANSWER).sampleCount());
    }

    @Test
    void recordsUnavailableAndExceptionalOutcomesWithoutTreatingThemAsPassEvidence() {
        AtomicLong clock = new AtomicLong();
        CandidateGenerationLatencyMetrics metrics = new CandidateGenerationLatencyMetrics(
                () -> clock.getAndAdd(1_000_000L));
        metrics.measure(CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                () -> SearchCompleteness.UNAVAILABLE, status -> status);
        assertThrows(IllegalStateException.class, () -> metrics.measure(
                CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                () -> { throw new IllegalStateException("search failed"); }, ignored -> SearchCompleteness.COMPLETE));

        assertEquals(CandidateGenerationLatencyMetrics.Outcome.UNAVAILABLE,
                metrics.samples(CandidateGenerationLatencyMetrics.Purpose.ANSWER).get(0).outcome());
        assertEquals(CandidateGenerationLatencyMetrics.Outcome.ERROR,
                metrics.samples(CandidateGenerationLatencyMetrics.Purpose.ANSWER).get(1).outcome());
        assertEquals(CandidateGenerationLatencyMetrics.Decision.UNMEASURED,
                metrics.summary(CandidateGenerationLatencyMetrics.Purpose.ANSWER).decision());
    }

    @Test
    void boundsRetainedSamplesAndMarksSlowP95AsFail() {
        AtomicLong clock = new AtomicLong();
        CandidateGenerationLatencyMetrics metrics = new CandidateGenerationLatencyMetrics(() -> clock.getAndAdd(400_000_000L));
        for (int i = 0; i < CandidateGenerationLatencyMetrics.MAX_SAMPLES_PER_PURPOSE + 1; i++) {
            int value = i;
            metrics.measure(CandidateGenerationLatencyMetrics.Purpose.ANSWER,
                    () -> value, ignored -> SearchCompleteness.COMPLETE);
        }
        var summary = metrics.summary(CandidateGenerationLatencyMetrics.Purpose.ANSWER);
        assertEquals(CandidateGenerationLatencyMetrics.MAX_SAMPLES_PER_PURPOSE, summary.sampleCount());
        assertEquals(400_000_000L, summary.p95Nanos().orElseThrow());
        assertEquals(CandidateGenerationLatencyMetrics.Decision.FAIL, summary.decision());
    }

    @Test
    void clockFailureDoesNotChangeOperationResultOrInventASample() {
        CandidateGenerationLatencyMetrics metrics = new CandidateGenerationLatencyMetrics(() -> {
            throw new IllegalStateException("clock unavailable");
        });

        String result = metrics.measure(CandidateGenerationLatencyMetrics.Purpose.RELATION,
                () -> "search-result", ignored -> SearchCompleteness.COMPLETE);

        assertEquals("search-result", result);
        assertEquals(0, metrics.summary(CandidateGenerationLatencyMetrics.Purpose.RELATION).sampleCount());
        assertEquals(CandidateGenerationLatencyMetrics.Decision.UNMEASURED,
                metrics.summary(CandidateGenerationLatencyMetrics.Purpose.RELATION).decision());
    }
}
