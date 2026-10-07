package com.projectalice.backend.ai.infrastructure.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.projectalice.backend.ai.application.model.AiCancellationToken;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.conversation.application.SendMessageUseCase;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenAiSendMessageWireMockTest {

    @RegisterExtension
    static final WireMockExtension WIRE_MOCK =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String RESPONSE_JSON = """
            {"id":"resp_1","object":"response","created_at":1,"status":"completed","error":null,
            "incomplete_details":null,"instructions":null,"metadata":{},"model":"test-model",
            "output":[{"type":"message","id":"msg_1","status":"completed","role":"assistant",
            "content":[{"type":"output_text","text":"Hello","annotations":[]}]}],
            "parallel_tool_calls":true,"temperature":1.0,"tool_choice":"auto","tools":[],"top_p":1.0}
            """.replace("\n", "");

    private static String sse() {
        return "event: response.output_text.delta\n"
                + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"output_index\":0,"
                + "\"content_index\":0,\"delta\":\"Hel\",\"sequence_number\":1,\"logprobs\":[]}\n\n"
                + "event: response.output_text.delta\n"
                + "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"output_index\":0,"
                + "\"content_index\":0,\"delta\":\"lo\",\"sequence_number\":2,\"logprobs\":[]}\n\n"
                + "event: response.completed\n"
                + "data: {\"type\":\"response.completed\",\"sequence_number\":3,\"response\":"
                + RESPONSE_JSON + "}\n\n";
    }

    @Test
    void sendMessageStreamsDeltasThroughOpenAiProviderAndReturnsResult() {
        WIRE_MOCK.stubFor(post(urlPathEqualTo("/responses")).willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(sse())));

        var settings = new OpenAiSettings("test-model", "low", 123);
        var executor = new OpenAiCallExecutor(
                () -> OpenAIOkHttpClient.builder()
                        .baseUrl(WIRE_MOCK.baseUrl())
                        .apiKey("mock-server-only")
                        .maxRetries(0)
                        .build(),
                Clock.systemUTC(), RandomGenerator.getDefault(), delay -> { });
        var provider = new OpenAiTextGenerationProvider(executor, new OpenAiRequestMapper(settings));

        OffsetDateTime now = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var history = List.of(new ConversationMessage("m1", 1, ConversationMessageRole.ASSISTANT, "earlier", now));
        var builder = new ConversationContextBuilder(
                limit -> history, (request, ctx) -> 10,
                new ConversationAiPolicy(64_000, 100, Duration.ofSeconds(60)));
        var current = new ConversationMessage("m2", 2, ConversationMessageRole.USER, "hi", now);
        var context = new AiExecutionContext(Instant.now().plusSeconds(30), new NeverCancelled());

        List<String> deltas = new ArrayList<>();
        TextGenerationResult result =
                new SendMessageUseCase(builder, provider).execute("be brief", current, context, deltas::add);

        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(result.content()).isEqualTo("Hello");
        assertThat(result.finishReason()).isEqualTo(TextGenerationFinishReason.COMPLETED);
        WIRE_MOCK.verify(1, postRequestedFor(urlPathEqualTo("/responses"))
                .withRequestBody(equalToJson("""
                        {"model":"test-model","instructions":"be brief","stream":true,"store":false,
                         "max_output_tokens":123,"reasoning":{"effort":"low"},
                         "input":[{"role":"assistant","content":"earlier"},{"role":"user","content":"hi"}]}
                        """, true, true)));
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
