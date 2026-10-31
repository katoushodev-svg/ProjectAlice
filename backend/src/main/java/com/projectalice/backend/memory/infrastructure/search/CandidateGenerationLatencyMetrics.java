package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.application.SearchCompleteness;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Content-free, bounded in-process samples for the DB2-065 candidate-generation interval. */
public final class CandidateGenerationLatencyMetrics {
    public enum Purpose { ANSWER, RELATION }
    public enum Outcome { COMPLETE, PARTIAL, UNAVAILABLE, ERROR }
    public enum Decision { PASS, FAIL, UNMEASURED }
    public record Sample(Purpose purpose, long durationNanos, Outcome outcome) {}
    public record Summary(long sampleCount, OptionalLong p95Nanos, Decision decision) {}

    public static final int MAX_SAMPLES_PER_PURPOSE = 1_000;
    public static final int MIN_SAMPLES_FOR_P95 = 20;
    public static final long TARGET_NANOS = 300_000_000L;

    private final LongSupplier nanoClock;
    private final ArrayDeque<Sample> answer = new ArrayDeque<>();
    private final ArrayDeque<Sample> relation = new ArrayDeque<>();

    public CandidateGenerationLatencyMetrics() { this(System::nanoTime); }

    public CandidateGenerationLatencyMetrics(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
    }

    /** Times one candidate-generation call. Instrumentation failures never change search behavior. */
    public <T> T measure(Purpose purpose, Supplier<T> operation,
            Function<T, SearchCompleteness> completeness) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(completeness, "completeness must not be null");
        Long start = readClock();
        Outcome outcome = Outcome.ERROR;
        try {
            T result = operation.get();
            SearchCompleteness status = completeness.apply(result);
            outcome = status == null ? Outcome.ERROR : switch (status) {
                case COMPLETE -> Outcome.COMPLETE;
                case PARTIAL -> Outcome.PARTIAL;
                case UNAVAILABLE -> Outcome.UNAVAILABLE;
            };
            return result;
        } finally {
            if (start != null) {
                Long finish = readClock();
                if (finish != null) {
                    long elapsed = finish - start;
                    if (elapsed >= 0) safelyRecord(new Sample(purpose, elapsed, outcome));
                }
            }
        }
    }

    public synchronized List<Sample> samples(Purpose purpose) {
        return List.copyOf(buffer(Objects.requireNonNull(purpose)));
    }

    public synchronized Summary summary(Purpose purpose) {
        List<Sample> samples = new ArrayList<>(buffer(Objects.requireNonNull(purpose)));
        if (samples.size() < MIN_SAMPLES_FOR_P95) {
            return new Summary(samples.size(), OptionalLong.empty(), Decision.UNMEASURED);
        }
        samples.sort(Comparator.comparingLong(Sample::durationNanos));
        int p95Index = (int) Math.ceil(0.95 * samples.size()) - 1;
        long p95 = samples.get(p95Index).durationNanos();
        return new Summary(samples.size(), OptionalLong.of(p95),
                p95 <= TARGET_NANOS ? Decision.PASS : Decision.FAIL);
    }

    private Long readClock() {
        try { return nanoClock.getAsLong(); }
        catch (RuntimeException ignored) { return null; }
    }

    private synchronized void safelyRecord(Sample sample) {
        try {
            ArrayDeque<Sample> values = buffer(sample.purpose());
            if (values.size() == MAX_SAMPLES_PER_PURPOSE) values.removeFirst();
            values.addLast(sample);
        } catch (RuntimeException ignored) {
            // Telemetry is best effort and must not alter the search result.
        }
    }

    private ArrayDeque<Sample> buffer(Purpose purpose) {
        return purpose == Purpose.ANSWER ? answer : relation;
    }
}
