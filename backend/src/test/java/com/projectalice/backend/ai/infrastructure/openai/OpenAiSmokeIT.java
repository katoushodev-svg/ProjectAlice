package com.projectalice.backend.ai.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.projectalice.backend.ai.application.model.AiCancellationToken;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.conversation.application.SendMessageUseCase;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("external-openai")
class OpenAiSmokeIT {

    @Test
    void sendMessageReachesRealOpenAiAndStreamsText() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY is not set; Real OpenAI not executed");

        var configuration = new OpenAiConfiguration();
        var settings = configuration.openAiSettings("gpt-5.6-terra", "low", 256);
        var executor = configuration.openAiCallExecutor(apiKey);
        var provider = configuration.openAiTextGenerationProvider(executor, settings);

        var builder = new ConversationContextBuilder(
                limit -> List.of(), (request, ctx) -> 10,
                new ConversationAiPolicy(64_000, 100, Duration.ofSeconds(60)));
        var current = new ConversationMessage(
                "m1", 1, ConversationMessageRole.USER, "Say hello in one short sentence.", OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var context = new AiExecutionContext(Instant.now().plusSeconds(60), new NeverCancelled());

        List<String> deltas = new ArrayList<>();
        TextGenerationResult result = new SendMessageUseCase(builder, provider)
                .execute("You are a concise assistant.", current, context, deltas::add);

        assertThat(deltas).isNotEmpty();
        assertThat(result.content()).isEqualTo(String.join("", deltas));
        assertThat(result.finishReason()).isNotNull();
    }

    private static final class NeverCancelled implements AiCancellationToken {
        @Override
        public boolean isCancellationRequested() {
            return false;
        }

        @Override
        public AutoCloseable onCancellation(Runnable callback) {
            return () -> { };
        }
    }
}
