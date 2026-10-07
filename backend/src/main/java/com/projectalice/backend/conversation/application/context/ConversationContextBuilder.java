package com.projectalice.backend.conversation.application.context;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiMessage;
import com.projectalice.backend.ai.application.model.AiMessageRole;
import com.projectalice.backend.ai.application.model.AiWorkload;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.port.InputTokenCounter;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ConversationContextBuilder {

    private final ConversationRepository conversationRepository;
    private final InputTokenCounter inputTokenCounter;
    private final ConversationAiPolicy policy;

    public ConversationContextBuilder(
            ConversationRepository conversationRepository,
            InputTokenCounter inputTokenCounter,
            ConversationAiPolicy policy
    ) {
        this.conversationRepository = Objects.requireNonNull(conversationRepository, "conversationRepository must not be null");
        this.inputTokenCounter = Objects.requireNonNull(inputTokenCounter, "inputTokenCounter must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public TextGenerationRequest build(
            String instructions,
            ConversationMessage currentUserMessage,
            AiExecutionContext executionContext
    ) {
        Objects.requireNonNull(instructions, "instructions must not be null");
        Objects.requireNonNull(currentUserMessage, "currentUserMessage must not be null");
        Objects.requireNonNull(executionContext, "executionContext must not be null");
        if (currentUserMessage.role() != ConversationMessageRole.USER) {
            throw new IllegalArgumentException("currentUserMessage must have USER role");
        }

        List<ConversationMessage> candidates =
                conversationRepository.findLatestMessages(policy.maxHistoryMessages());

        List<ConversationMessage> history = new ArrayList<>();
        if (candidates != null) {
            for (ConversationMessage candidate : candidates) {
                if (candidate == null
                        || candidate.content() == null
                        || candidate.content().isBlank()
                        || candidate.messageId().equals(currentUserMessage.messageId())) {
                    continue;
                }
                history.add(candidate);
            }
        }
        history.sort(Comparator.comparingLong(ConversationMessage::sequence));

        List<AiMessage> messages = new ArrayList<>(history.size() + 1);
        for (ConversationMessage message : history) {
            messages.add(toAiMessage(message));
        }
        messages.add(toAiMessage(currentUserMessage));

        TextGenerationRequest request =
                new TextGenerationRequest(AiWorkload.CONVERSATION_REPLY, instructions, messages);

        long inputTokens = inputTokenCounter.count(request, executionContext);
        if (inputTokens > policy.maxInputTokens()) {
            // Over-budget handling is not confirmed in the design yet.
            throw new UnsupportedOperationException("Input token budget exceeded; handling is not yet defined");
        }
        return request;
    }

    private static AiMessage toAiMessage(ConversationMessage message) {
        AiMessageRole role = switch (message.role()) {
            case USER -> AiMessageRole.USER;
            case ASSISTANT -> AiMessageRole.ASSISTANT;
        };
        return new AiMessage(role, message.content());
    }
}
