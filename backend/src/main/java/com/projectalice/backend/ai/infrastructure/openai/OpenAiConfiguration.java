package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.AiGenerationException;
import com.projectalice.backend.ai.application.model.AiGenerationFailure;
import com.projectalice.backend.ai.application.port.InputTokenCounter;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import java.time.Clock;
import java.util.random.RandomGenerator;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenAiConfiguration {

    @Bean
    OpenAiSettings openAiSettings(
            @Value("${ALICE_OPENAI_CONVERSATION_MODEL:gpt-5.6-terra}") String model,
            @Value("${ALICE_OPENAI_CONVERSATION_REASONING_EFFORT:low}") String reasoningEffort,
            @Value("${ALICE_OPENAI_CONVERSATION_MAX_OUTPUT_TOKENS:4096}") long maxOutputTokens
    ) {
        return new OpenAiSettings(model, reasoningEffort, maxOutputTokens);
    }

    @Bean
    OpenAiCallExecutor openAiCallExecutor(@Value("${OPENAI_API_KEY:}") String apiKey) {
        return new OpenAiCallExecutor(
                lazyClient(apiKey),
                Clock.systemUTC(),
                RandomGenerator.getDefault(),
                Thread::sleep
        );
    }

    @Bean
    TextGenerationProvider openAiTextGenerationProvider(OpenAiCallExecutor executor, OpenAiSettings settings) {
        return new OpenAiTextGenerationProvider(executor, new OpenAiRequestMapper(settings));
    }

    @Bean
    InputTokenCounter openAiInputTokenCounter(OpenAiCallExecutor executor, OpenAiSettings settings) {
        return new OpenAiInputTokenCounter(executor, new OpenAiRequestMapper(settings));
    }

    // The client is built on first use so a missing key fails the call, not application startup.
    private static Supplier<OpenAIClient> lazyClient(String apiKey) {
        return new Supplier<>() {
            private OpenAIClient client;

            @Override
            public synchronized OpenAIClient get() {
                if (client == null) {
                    client = createClient(apiKey);
                }
                return client;
            }
        };
    }

    static OpenAIClient createClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiGenerationException(new AiGenerationFailure(
                    AiFailureCategory.AUTHENTICATION_OR_CONFIGURATION,
                    "AI provider is not configured.",
                    false));
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Timeout.builder().connect(OpenAiCallExecutor.CONNECT_TIMEOUT).build())
                .maxRetries(0)
                .build();
    }
}
