package com.projectalice.backend.ai.application.port;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;

public interface InputTokenCounter {

    long count(
            TextGenerationRequest request,
            AiExecutionContext executionContext
    );
}
