package com.projectalice.backend.conversation.application;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.port.TextDeltaHandler;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import java.util.Objects;

// Normal path only: failure, persistence, deadline generation and idempotency are not yet designed here.
public final class SendMessageUseCase {

    private final ConversationContextBuilder contextBuilder;
    private final TextGenerationProvider textGenerationProvider;

    public SendMessageUseCase(
            ConversationContextBuilder contextBuilder,
            TextGenerationProvider textGenerationProvider
    ) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.textGenerationProvider =
                Objects.requireNonNull(textGenerationProvider, "textGenerationProvider must not be null");
    }

    public TextGenerationResult execute(
            String instructions,
            ConversationMessage currentUserMessage,
            AiExecutionContext executionContext,
            TextDeltaHandler deltaHandler
    ) {
        Objects.requireNonNull(deltaHandler, "deltaHandler must not be null");
        TextGenerationRequest request = contextBuilder.build(instructions, currentUserMessage, executionContext);
        return textGenerationProvider.generate(request, executionContext, deltaHandler);
    }
}
