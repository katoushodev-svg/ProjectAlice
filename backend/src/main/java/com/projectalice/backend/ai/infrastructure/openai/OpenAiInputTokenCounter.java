package com.projectalice.backend.ai.infrastructure.openai;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.port.InputTokenCounter;

final class OpenAiInputTokenCounter implements InputTokenCounter {

    private final OpenAiCallExecutor executor;
    private final OpenAiRequestMapper requestMapper;

    OpenAiInputTokenCounter(OpenAiCallExecutor executor, OpenAiRequestMapper requestMapper) {
        this.executor = executor;
        this.requestMapper = requestMapper;
    }

    @Override
    public long count(TextGenerationRequest request, AiExecutionContext executionContext) {
        return executor.execute(executionContext, () -> true, (client, options) ->
                client.responses().inputTokens()
                        .count(requestMapper.toCountParams(request), options)
                        .inputTokens());
    }
}
