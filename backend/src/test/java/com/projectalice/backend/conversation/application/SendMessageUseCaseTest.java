package com.projectalice.backend.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiMessage;
import com.projectalice.backend.ai.application.model.AiMessageRole;
import com.projectalice.backend.ai.application.model.AiWorkload;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.port.TextDeltaHandler;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SendMessageUseCaseTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final AiExecutionContext EXECUTION_CONTEXT =
            new AiExecutionContext(Instant.parse("2026-01-01T00:00:00Z"), null);

    private static final class FakeProvider implements TextGenerationProvider {
        final List<TextGenerationRequest> requests = new ArrayList<>();
        final TextGenerationResult result =
                new TextGenerationResult("reply", TextGenerationFinishReason.COMPLETED);

        @Override
        public TextGenerationResult generate(
                TextGenerationRequest request, AiExecutionContext context, TextDeltaHandler deltaHandler) {
            requests.add(request);
            deltaHandler.onDelta("reply");
            return result;
        }
    }

    @Test
    void passesBuiltRequestToProviderAndReturnsItsResult() {
        List<Integer> historyLimits = new ArrayList<>();
        List<ConversationMessage> history = List.of(
                new ConversationMessage("m1", 1, ConversationMessageRole.ASSISTANT, "a1", NOW));
        ConversationContextBuilder builder = new ConversationContextBuilder(
                limit -> {
                    historyLimits.add(limit);
                    return history;
                },
                (request, ctx) -> 10,
                new ConversationAiPolicy(64_000, 100, Duration.ofSeconds(60)));
        FakeProvider provider = new FakeProvider();
        List<String> deltas = new ArrayList<>();
        ConversationMessage current = new ConversationMessage("m2", 2, ConversationMessageRole.USER, "now", NOW);

        TextGenerationResult result =
                new SendMessageUseCase(builder, provider).execute("inst", current, EXECUTION_CONTEXT, deltas::add);

        assertThat(historyLimits).hasSize(1);
        assertThat(provider.requests).hasSize(1);
        TextGenerationRequest request = provider.requests.getFirst();
        assertThat(request.workload()).isEqualTo(AiWorkload.CONVERSATION_REPLY);
        assertThat(request.instructions()).isEqualTo("inst");
        assertThat(request.messages()).containsExactly(
                new AiMessage(AiMessageRole.ASSISTANT, "a1"),
                new AiMessage(AiMessageRole.USER, "now"));
        assertThat(request.messages().getLast()).isEqualTo(new AiMessage(AiMessageRole.USER, "now"));
        assertThat(result).isSameAs(provider.result);
        assertThat(deltas).containsExactly("reply");
    }
}
