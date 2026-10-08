package com.projectalice.backend.conversation;

import com.projectalice.backend.ai.application.port.InputTokenCounter;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.ConversationSendService;
import com.projectalice.backend.conversation.application.ConversationReadService;
import com.projectalice.backend.conversation.application.SendMessageUseCase;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.application.prompt.ConversationPromptComposer;
import com.projectalice.backend.conversation.infrastructure.persistence.dynamodb.DynamoDbConversationRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Clock;
import java.time.Duration;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration(proxyBeanMethods = false)
public class ConversationConfiguration {
    @Bean Clock aliceClock() { return Clock.systemUTC(); }

    @Bean DynamoDbClient conversationDynamoDbClient(
            @Value("${ALICE_DYNAMODB_ENDPOINT:http://localhost:8000}") URI endpoint,
            @Value("${AWS_REGION:ap-northeast-1}") String region,
            @Value("${AWS_ACCESS_KEY_ID:local}") String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY:local}") String secretKey) {
        String host = endpoint.getHost();
        if (host == null || !(host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")))
            throw new IllegalArgumentException("DynamoDB Local endpoint must use loopback");
        return DynamoDbClient.builder().endpointOverride(endpoint).region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();
    }

    @Bean ConversationRepository conversationRepository(DynamoDbClient client,
            @Value("${ALICE_DYNAMODB_TABLE_NAME:project-alice-conversation-local}") String table) {
        return new DynamoDbConversationRepository(client, table);
    }

    @Bean ConversationReadService conversationReadService(ConversationRepository repository,
            @Value("${ALICE_CURSOR_SIGNING_KEY:}") String encodedKey) {
        if (encodedKey.isBlank()) throw new IllegalArgumentException("ALICE_CURSOR_SIGNING_KEY is required");
        try { return new ConversationReadService(repository, Base64.getDecoder().decode(encodedKey)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("ALICE_CURSOR_SIGNING_KEY must be Base64", invalid); }
    }

    @Bean ConversationPromptComposer conversationPromptComposer(
            @Value("classpath:prompts/alice/base-identity.md") Resource base,
            @Value("classpath:prompts/conversation/reply-policy.md") Resource policy) throws IOException {
        return new ConversationPromptComposer(read(base), read(policy));
    }
    private static String read(Resource resource) throws IOException {
        try (var in = resource.getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Bean ConversationAiPolicy conversationAiPolicy(
            @Value("${ALICE_AI_CONVERSATION_MAX_INPUT_TOKENS:64000}") long tokens,
            @Value("${ALICE_AI_CONVERSATION_MAX_HISTORY_MESSAGES:100}") int history,
            @Value("${ALICE_AI_CONVERSATION_USE_CASE_TIMEOUT:170s}") Duration timeout) {
        return new ConversationAiPolicy(tokens, history, timeout);
    }
    @Bean ConversationContextBuilder conversationContextBuilder(ConversationRepository repository,
            InputTokenCounter counter, ConversationAiPolicy policy) {
        return new ConversationContextBuilder(repository, counter, policy);
    }
    @Bean SendMessageUseCase sendMessageUseCase(ConversationContextBuilder builder, TextGenerationProvider provider) {
        return new SendMessageUseCase(builder, provider);
    }
    @Bean ConversationSendService conversationSendService(ConversationRepository repository, SendMessageUseCase useCase,
            ConversationPromptComposer prompt, Clock clock,
            @Value("${alice.api.conversation.sse-timeout:180s}") Duration sseTimeout,
            @Value("${ALICE_PROCESSING_LEASE_SECONDS:300}") long processingLeaseSeconds,
            @Value("${ALICE_FAILED_RETENTION_SECONDS:86400}") long failedRetentionSeconds,
            @Value("${alice.ai.conversation.use-case-timeout:170s}") Duration useCaseTimeout) {
        return new ConversationSendService(repository, useCase, prompt, clock, sseTimeout,
                Duration.ofSeconds(processingLeaseSeconds), Duration.ofSeconds(failedRetentionSeconds),useCaseTimeout);
    }
    @Bean Executor conversationSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); executor.setMaxPoolSize(16); executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("conversation-sse-"); executor.initialize(); return executor;
    }
    @Bean(destroyMethod = "shutdown") ScheduledExecutorService conversationHeartbeatScheduler() {
        return Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "conversation-heartbeat"); thread.setDaemon(true); return thread;
        });
    }
}
