package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.AiGenerationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Owns Deadline / Cancellation checks and the Phase 1 retry policy (max 2 attempts, SDK retry disabled). */
final class OpenAiCallExecutor {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 2;
    private static final long BASE_DELAY_MILLIS = 250;
    private static final long MAX_JITTER_MILLIS = 100;

    @FunctionalInterface
    interface Attempt<T> {
        T run(OpenAIClient client, RequestOptions options);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    private final Supplier<OpenAIClient> clientSupplier;
    private final Clock clock;
    private final RandomGenerator random;
    private final Sleeper sleeper;

    OpenAiCallExecutor(Supplier<OpenAIClient> clientSupplier, Clock clock, RandomGenerator random, Sleeper sleeper) {
        this.clientSupplier = clientSupplier;
        this.clock = clock;
        this.random = random;
        this.sleeper = sleeper;
    }

    <T> T execute(AiExecutionContext context, BooleanSupplier retryAllowed, Attempt<T> attempt) {
        Objects.requireNonNull(context, "context must not be null");
        for (int attemptNumber = 1; ; attemptNumber++) {
            checkContext(context);
            try {
                OpenAIClient client = clientSupplier.get();
                return attempt.run(client, requestOptions(context));
            } catch (RuntimeException e) {
                checkContext(context);
                if (attemptNumber >= MAX_ATTEMPTS
                        || !retryAllowed.getAsBoolean()
                        || !OpenAiFailureMapper.isRetryable(e)) {
                    throw OpenAiFailureMapper.map(e);
                }
                Duration delay = OpenAiFailureMapper.retryAfter(e)
                        .orElseGet(this::defaultDelay);
                if (remaining(context).compareTo(delay) <= 0) {
                    throw OpenAiFailureMapper.map(e);
                }
                sleep(delay);
            }
        }
    }

    void checkContext(AiExecutionContext context) {
        // Deadline expiry also cancels the token, so it must be evaluated first to yield TIMEOUT.
        if (!remaining(context).isPositive()) {
            throw OpenAiFailureMapper.exception(AiFailureCategory.TIMEOUT);
        }
        if (context.cancellationToken().isCancellationRequested()) {
            throw OpenAiFailureMapper.exception(AiFailureCategory.CANCELLED);
        }
    }

    private Duration remaining(AiExecutionContext context) {
        return Duration.between(Instant.now(clock), context.deadline());
    }

    private Duration defaultDelay() {
        return Duration.ofMillis(BASE_DELAY_MILLIS + random.nextLong(MAX_JITTER_MILLIS + 1));
    }

    private RequestOptions requestOptions(AiExecutionContext context) {
        Duration remaining = remaining(context);
        Duration connect = remaining.compareTo(CONNECT_TIMEOUT) < 0 ? remaining : CONNECT_TIMEOUT;
        return RequestOptions.builder()
                .timeout(Timeout.builder()
                        .connect(connect)
                        .read(remaining)
                        .write(remaining)
                        .request(remaining)
                        .build())
                .build();
    }

    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OpenAiFailureMapper.exception(AiFailureCategory.CANCELLED);
        }
    }
}
