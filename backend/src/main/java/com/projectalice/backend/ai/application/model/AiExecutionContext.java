package com.projectalice.backend.ai.application.model;

import java.time.Instant;

public record AiExecutionContext(
        Instant deadline,
        AiCancellationToken cancellationToken
) {
}
