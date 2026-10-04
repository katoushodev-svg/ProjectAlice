package com.projectalice.backend.ai.application.port;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.model.TextGenerationResult;

public interface TextGenerationProvider {

    TextGenerationResult generate(
            TextGenerationRequest request,
            AiExecutionContext executionContext,
            TextDeltaHandler deltaHandler
    );
}
