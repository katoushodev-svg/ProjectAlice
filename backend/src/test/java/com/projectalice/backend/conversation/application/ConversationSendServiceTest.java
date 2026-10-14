package com.projectalice.backend.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.AiGenerationException;
import com.projectalice.backend.ai.application.model.AiGenerationFailure;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.application.prompt.ConversationPromptComposer;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConversationSendServiceTest {
    private static final class MemoryRepository implements ConversationRepository {
        final List<ConversationMessage> history = new ArrayList<>();
        ConversationState conversation;
        IdempotencyState idempotency;
        int starts;
        @Override public List<ConversationMessage> findLatestMessages(int limit) { return List.copyOf(history); }
        @Override public List<ConversationMessage> findMessagesBefore(long sequence, int limit) {
            return history.stream().filter(message -> message.sequence() < sequence).sorted(java.util.Comparator.comparingLong(ConversationMessage::sequence).reversed()).limit(limit).toList();
        }
        @Override public Optional<ConversationState> findConversation() { return Optional.ofNullable(conversation); }
        @Override public Optional<IdempotencyState> findIdempotency(String key) { return Optional.ofNullable(idempotency); }
        @Override public void start(SendStart start) {
            starts++;
            history.add(start.userMessage());
            conversation = new ConversationState(start.conversationId(), start.now(), start.now(), start.userMessage().sequence(),
                    start.key(), start.leaseExpiresAtEpochSeconds());
            idempotency = new IdempotencyState(start.key(), start.contentHash(), "PROCESSING", start.userMessage(), null, null,
                    start.leaseExpiresAtEpochSeconds(), 0);
        }
        @Override public void complete(String key, ConversationMessage assistant) {
            history.add(assistant);
            conversation = new ConversationState(conversation.id(), conversation.createdAt(), assistant.createdAt(), assistant.sequence(), null, 0);
            idempotency = new IdempotencyState(key,idempotency.contentHash(),"COMPLETED",idempotency.userMessage(),assistant,null,0,0);
        }
        @Override public void recoverExpired(String key, java.time.OffsetDateTime at, long expiry) {
            fail(key, "REQUEST_INTERRUPTED", at, expiry);
        }
        @Override public void fail(String key,String code,java.time.OffsetDateTime at,long expiry) {
            conversation = new ConversationState(conversation.id(),conversation.createdAt(),conversation.updatedAt(),conversation.lastSequence(),null,0);
            idempotency = new IdempotencyState(key,idempotency.contentHash(),"FAILED",idempotency.userMessage(),null,code,0,expiry);
        }
    }

    private static ConversationSendService create(MemoryRepository repository, int[] generations) {
        TextGenerationProvider provider = (request, context, delta) -> {
            generations[0]++;
            delta.onDelta("お返事です");
            return new TextGenerationResult("お返事です", TextGenerationFinishReason.COMPLETED);
        };
        var builder = new ConversationContextBuilder(repository, (request, context) -> 5,
                new ConversationAiPolicy(64_000,100,Duration.ofSeconds(170)));
        return new ConversationSendService(repository,new SendMessageUseCase(builder,provider),
                new ConversationPromptComposer("identity","policy"), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(180), Duration.ofSeconds(300), Duration.ofHours(24), Duration.ofSeconds(170));
    }

    @Test void persistsCanonicalMessagesAndProducesOrderedTerminalEventsWithOneRequestId() {
        MemoryRepository repository = new MemoryRepository();
        int[] calls = {0};
        var service=create(repository,calls);
        var prepared=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","こんにちは","f1138625-44d8-4946-b665-89a737c577d6");
        List<ConversationSendService.StreamEvent> events=new ArrayList<>();
        service.stream(prepared,events::add);
        assertThat(events).extracting(ConversationSendService.StreamEvent::name)
                .containsExactly("stream.started","assistant.delta","assistant.completed");
        var start=(ConversationSendService.Started)events.getFirst().data();
        var done=(ConversationSendService.Completed)events.getLast().data();
        assertThat(start.userMessage()).isEqualTo(done.userMessage());
        assertThat(((ConversationSendService.Delta)events.get(1).data()).requestId()).isEqualTo(start.requestId());
        assertThat(done.requestId()).isEqualTo(start.requestId());
        assertThat(done.assistantMessage().content()).isEqualTo("お返事です");
        assertThat(com.projectalice.backend.conversation.domain.ConversationDateTime.format(start.userMessage().createdAt()))
                .isEqualTo("2026-01-01T09:00:00.000+09:00");
        assertThat(repository.history).hasSize(2);
        assertThat(repository.idempotency.state()).isEqualTo("COMPLETED");
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test void completedRetryReplaysCanonicalResultWithoutRepeatingGeneration() {
        MemoryRepository repository=new MemoryRepository(); int[] calls={0}; var service=create(repository,calls);
        var first=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-1");
        service.stream(first,event -> { });
        var retry=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-2");
        List<ConversationSendService.StreamEvent> events=new ArrayList<>(); service.stream(retry,events::add);
        assertThat(events).extracting(ConversationSendService.StreamEvent::name).containsExactly("stream.started","assistant.completed");
        assertThat(((ConversationSendService.Started)events.getFirst().data()).requestId()).isEqualTo("request-2");
        assertThat(((ConversationSendService.Completed)events.getLast().data()).requestId()).isEqualTo("request-2");
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test void sameKeyWithDifferentPayloadIsRejectedBeforePersistence() {
        MemoryRepository repository=new MemoryRepository(); int[] calls={0}; var service=create(repository,calls);
        service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-1");
        assertThatThrownBy(() -> service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","different","request-2"))
                .isInstanceOf(ConversationSendException.class).hasMessageContaining("異なる内容");
        assertThat(repository.starts).isEqualTo(1);
    }

    @Test void providerFailureIsPersistedAndReplayedWithoutCallingProviderAgain() {
        MemoryRepository repository=new MemoryRepository();
        int[] calls={0};
        TextGenerationProvider provider=(request,context,delta) -> {
            calls[0]++;
            throw new AiGenerationException(new AiGenerationFailure(AiFailureCategory.TEMPORARILY_UNAVAILABLE,"Provider unavailable",true));
        };
        var builder=new ConversationContextBuilder(repository,(request,context)->1,
                new ConversationAiPolicy(64_000,100,Duration.ofSeconds(170)));
        var service=new ConversationSendService(repository,new SendMessageUseCase(builder,provider),
                new ConversationPromptComposer("identity","policy"),Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),
                Duration.ofSeconds(180),Duration.ofSeconds(300),Duration.ofHours(24),Duration.ofSeconds(170));
        var first=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-1");
        List<ConversationSendService.StreamEvent> firstEvents=new ArrayList<>(); service.stream(first,firstEvents::add);
        assertThat(firstEvents).extracting(ConversationSendService.StreamEvent::name).containsExactly("stream.started","stream.failed");
        assertThat(repository.idempotency.state()).isEqualTo("FAILED");
        assertThat(repository.idempotency.failureCode()).isEqualTo("RESPONSE_GENERATION_FAILED");
        assertThat(repository.history).hasSize(1);
        var retry=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-2");
        List<ConversationSendService.StreamEvent> replay=new ArrayList<>(); service.stream(retry,replay::add);
        assertThat(replay).extracting(ConversationSendService.StreamEvent::name).containsExactly("stream.started","stream.failed");
        assertThat(((ConversationSendService.Failed)replay.getLast().data()).requestId()).isEqualTo("request-2");
        assertThat(calls[0]).isEqualTo(1);
    }

    @Test void timeoutCancelsGenerationPersistsFailureAndIgnoresLateProviderResult() throws Exception {
        MemoryRepository repository=new MemoryRepository();
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        TextGenerationProvider provider=(request,context,delta) -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            delta.onDelta("late answer");
            return new TextGenerationResult("late answer",TextGenerationFinishReason.COMPLETED);
        };
        var builder=new ConversationContextBuilder(repository,(request,context)->1,
                new ConversationAiPolicy(64_000,100,Duration.ofSeconds(170)));
        var service=new ConversationSendService(repository,new SendMessageUseCase(builder,provider),
                new ConversationPromptComposer("identity","policy"),Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC),
                Duration.ofSeconds(180),Duration.ofSeconds(300),Duration.ofHours(24),Duration.ofSeconds(170));
        var prepared=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-timeout");
        var cancellation=new ConversationSendService.ExecutionCancellation();
        List<ConversationSendService.StreamEvent> events=new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread worker=new Thread(() -> service.stream(prepared,events::add,cancellation));
        worker.start();
        assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
        service.timeout(prepared,cancellation,events::add);
        release.countDown();
        worker.join(2_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(repository.idempotency.state()).isEqualTo("FAILED");
        assertThat(repository.idempotency.failureCode()).isEqualTo("RESPONSE_TIMEOUT");
        assertThat(repository.history).hasSize(1);
        assertThat(events).extracting(ConversationSendService.StreamEvent::name)
                .containsExactly("stream.started","stream.failed");
        assertThat(((ConversationSendService.Failed)events.getLast().data()).code()).isEqualTo("RESPONSE_TIMEOUT");
    }

    @Test void executorRejectionPersistsExistingInterruptedFailureAndEmitsTerminalEvent() {
        MemoryRepository repository=new MemoryRepository();
        var service=create(repository,new int[]{0});
        var prepared=service.prepare("a0b73d9c-1fd3-49c0-b818-e271f3137064","hello","request-rejected");
        List<ConversationSendService.StreamEvent> events=new ArrayList<>();
        service.rejected(prepared,events::add);
        assertThat(repository.idempotency.state()).isEqualTo("FAILED");
        assertThat(repository.idempotency.failureCode()).isEqualTo("REQUEST_INTERRUPTED");
        assertThat(events).extracting(ConversationSendService.StreamEvent::name)
                .containsExactly("stream.started","stream.failed");
        assertThat(((ConversationSendService.Failed)events.getLast().data()).code()).isEqualTo("REQUEST_INTERRUPTED");
    }
}
