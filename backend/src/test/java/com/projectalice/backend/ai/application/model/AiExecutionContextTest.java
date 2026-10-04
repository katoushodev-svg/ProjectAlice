package com.projectalice.backend.ai.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiExecutionContextTest {

    @Test
    void createsExecutionContext() {
        Instant deadline = Instant.parse("2026-10-04T12:00:00Z");
        AiCancellationToken cancellationToken = new AiCancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @Override
            public AutoCloseable onCancellation(Runnable callback) {
                return () -> { };
            }
        };

        AiExecutionContext context = new AiExecutionContext(deadline, cancellationToken);

        assertEquals(deadline, context.deadline());
        assertEquals(cancellationToken, context.cancellationToken());
    }
}
