package com.projectalice.backend.conversation.application;

import com.projectalice.backend.ai.application.model.AiCancellationToken;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiGenerationException;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.port.TextDeltaHandler;
import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.application.model.ConversationPersistenceConflictException;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.application.port.ConversationRepository.IdempotencyState;
import com.projectalice.backend.conversation.application.port.ConversationRepository.SendStart;
import com.projectalice.backend.conversation.application.prompt.ConversationPromptComposer;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Coordinates durable start, AI generation, and terminal persistence for one logical send. */
public final class ConversationSendService {
    private static final ZoneOffset JST = ZoneOffset.ofHours(9);
    private static final int MAX_DELTA_CODE_POINTS = 8_000;
    private final Duration streamTimeout;
    private final Duration processingLease;
    private final Duration failedRetention;
    private final Duration useCaseTimeout;
    private final ConversationRepository repository;
    private final SendMessageUseCase sendMessage;
    private final ConversationPromptComposer prompt;
    private final Clock clock;

    public ConversationSendService(ConversationRepository repository, SendMessageUseCase sendMessage,
                                   ConversationPromptComposer prompt, Clock clock, Duration streamTimeout,
                                   Duration processingLease, Duration failedRetention, Duration useCaseTimeout) {
        this.repository = Objects.requireNonNull(repository);
        this.sendMessage = Objects.requireNonNull(sendMessage);
        this.prompt = Objects.requireNonNull(prompt);
        this.clock = Objects.requireNonNull(clock);
        this.streamTimeout = Objects.requireNonNull(streamTimeout);
        this.processingLease = Objects.requireNonNull(processingLease);
        this.failedRetention = Objects.requireNonNull(failedRetention);
        this.useCaseTimeout = Objects.requireNonNull(useCaseTimeout);
        if (streamTimeout.isNegative() || streamTimeout.isZero() || processingLease.compareTo(streamTimeout.plusSeconds(60)) < 0
                || failedRetention.compareTo(Duration.ofHours(24)) < 0 || useCaseTimeout.isNegative()
                || useCaseTimeout.isZero() || useCaseTimeout.compareTo(streamTimeout) >= 0)
            throw new IllegalArgumentException("Invalid conversation timeout / lease / retention configuration");
    }

    public PreparedSend prepare(String key, String content, String requestId) {
        OffsetDateTime now = now();
        String hash = sha256(content);
        IdempotencyState existing = repository.findIdempotency(key).orElse(null);
        if (existing != null && "FAILED".equals(existing.state())
                && existing.expiresAtEpochSeconds() <= now.toEpochSecond()) existing = null;
        if (existing != null) {
            if (!existing.contentHash().equals(hash)) throw problem("IDEMPOTENCY_KEY_CONFLICT", 409,
                    "このIdempotency-Keyは異なる内容で既に使用されています。");
            if ("PROCESSING".equals(existing.state())) {
                if (existing.leaseExpiresAtEpochSeconds() <= now.toEpochSecond()) {
                    repository.recoverExpired(key, now, now.toEpochSecond() + failedRetention.toSeconds());
                    existing = repository.findIdempotency(key).orElse(existing);
                } else throw problem("REQUEST_IN_PROGRESS", 409, "同じ送信を処理中です。");
            }
            return new PreparedSend(key, content, requestId, existing, false);
        }
        var conversation = repository.findConversation().orElse(null);
        if (conversation != null && conversation.activeIdempotencyKey() != null) {
            if (conversation.busyLeaseExpiresAtEpochSeconds() <= now.toEpochSecond()) {
                repository.recoverExpired(conversation.activeIdempotencyKey(), now,
                        now.toEpochSecond() + failedRetention.toSeconds());
                conversation = repository.findConversation().orElse(null);
            }
            if (conversation != null && conversation.activeIdempotencyKey() != null)
                throw problem("CONVERSATION_BUSY", 409, "Aliceが応答中です。完了後にもう一度送信してください。");
        }
        ConversationMessage user = new ConversationMessage(UUID.randomUUID().toString(),
                conversation == null ? 1 : conversation.lastSequence() + 1,
                ConversationMessageRole.USER, content, now);
        try {
            repository.start(new SendStart(key, hash, user, now, now.toEpochSecond() + processingLease.toSeconds(),
                    conversation == null ? UUID.randomUUID().toString() : conversation.id()));
        } catch (ConversationPersistenceConflictException race) {
            IdempotencyState raced = repository.findIdempotency(key).orElse(null);
            if (raced != null) return prepare(key, content, requestId);
            var current = repository.findConversation().orElse(null);
            if (current != null && current.activeIdempotencyKey() != null)
                throw problem("CONVERSATION_BUSY", 409, "Aliceが応答中です。完了後にもう一度送信してください。");
            throw new ConversationSendException("SERVICE_UNAVAILABLE", 503, "Conversationの状態を確定できませんでした。");
        }
        return new PreparedSend(key, content, requestId,
                new IdempotencyState(key, hash, "PROCESSING", user, null, null,
                        now.toEpochSecond() + processingLease.toSeconds(), 0), true);
    }

    public void stream(PreparedSend prepared, Consumer<StreamEvent> output) {
        stream(prepared, output, new ExecutionCancellation());
    }

    public void stream(PreparedSend prepared, Consumer<StreamEvent> output, ExecutionCancellation cancellation) {
        IdempotencyState state = prepared.state();
        ConversationMessage user = state.userMessage();
        output.accept(new StreamEvent("stream.started", new Started(prepared.requestId(), user)));
        if (cancellation.isCancellationRequested()) return;
        if (!prepared.newExecution()) {
            if ("COMPLETED".equals(state.state())) {
                output.accept(new StreamEvent("assistant.completed",
                        new Completed(prepared.requestId(), user, state.assistantMessage())));
            } else {
                output.accept(new StreamEvent("stream.failed", new Failed(prepared.requestId(), state.failureCode(), safeMessage(state.failureCode()))));
            }
            return;
        }
        StringBuilder response = new StringBuilder();
        StringBuilder pendingDelta = new StringBuilder();
        try {
            Instant deadline = clock.instant().plus(useCaseTimeout);
            TextGenerationResult result = sendMessage.execute(prompt.compose(), user,
                    new AiExecutionContext(deadline, cancellation), delta -> {
                        if (delta == null || delta.isEmpty()) return;
                        if (response.codePointCount(0, response.length()) +
                                delta.codePointCount(0, delta.length()) > 50_000) throw new IllegalStateException("response length exceeded");
                        response.append(delta);
                        pendingDelta.append(delta);
                        flushDeltas(prepared.requestId(), pendingDelta, output, false);
                    });
            if (result.finishReason() != TextGenerationFinishReason.COMPLETED || response.isEmpty()
                    || !result.content().equals(response.toString()))
                throw new IllegalStateException("generation did not complete");
            synchronized (cancellation) {
                if (cancellation.isCancellationRequested() || cancellation.terminal()) return;
                flushDeltas(prepared.requestId(), pendingDelta, output, true);
                OffsetDateTime completedAt = now();
                ConversationMessage assistant = new ConversationMessage(UUID.randomUUID().toString(), user.sequence() + 1,
                        ConversationMessageRole.ASSISTANT, result.content(), completedAt);
                try {
                    repository.complete(prepared.key(), assistant);
                } catch (RuntimeException persistenceFailure) {
                    IdempotencyState durable = repository.findIdempotency(prepared.key()).orElse(null);
                    if (durable != null && "COMPLETED".equals(durable.state())) {
                        assistant = durable.assistantMessage();
                    } else {
                        terminalFailure(prepared, output, "MESSAGE_SAVE_FAILED");
                        cancellation.markTerminal();
                        return;
                    }
                }
                output.accept(new StreamEvent("assistant.completed", new Completed(prepared.requestId(), user, assistant)));
                cancellation.markTerminal();
            }
        } catch (RuntimeException failure) {
            synchronized (cancellation) {
                if (cancellation.terminal()) return;
            String code = failure instanceof AiGenerationException aiFailure
                    && aiFailure.failure().category() == AiFailureCategory.TIMEOUT || cancellation.isCancellationRequested()
                    ? "RESPONSE_TIMEOUT" : "RESPONSE_GENERATION_FAILED";
            terminalFailure(prepared, output, code);
                cancellation.markTerminal();
            }
        }
    }

    public void timeout(PreparedSend prepared, ExecutionCancellation cancellation, Consumer<StreamEvent> output) {
        cancellation.cancel();
        synchronized (cancellation) {
            if (cancellation.terminal()) return;
            terminalFailure(prepared, output, "RESPONSE_TIMEOUT");
            cancellation.markTerminal();
        }
    }

    public void rejected(PreparedSend prepared, Consumer<StreamEvent> output) {
        output.accept(new StreamEvent("stream.started", new Started(prepared.requestId(), prepared.state().userMessage())));
        terminalFailure(prepared, output, "REQUEST_INTERRUPTED");
    }

    private void terminalFailure(PreparedSend prepared, Consumer<StreamEvent> output, String code) {
        OffsetDateTime failedAt = now();
        try { repository.fail(prepared.key(), code, failedAt, failedAt.toEpochSecond() + failedRetention.toSeconds()); }
        catch (RuntimeException ignored) { /* Lease recovery is the durable fallback if terminal persistence fails. */ }
        output.accept(new StreamEvent("stream.failed", new Failed(prepared.requestId(), code, safeMessage(code))));
    }

    private static void flushDeltas(String requestId, StringBuilder pending, Consumer<StreamEvent> output, boolean all) {
        while (!pending.isEmpty()) {
            int points = pending.codePointCount(0, pending.length());
            if (!all && points < MAX_DELTA_CODE_POINTS) return;
            int count = all ? Math.min(points, MAX_DELTA_CODE_POINTS) : MAX_DELTA_CODE_POINTS;
            int end = pending.offsetByCodePoints(0, count);
            String part = pending.substring(0, end);
            pending.delete(0, end);
            output.accept(new StreamEvent("assistant.delta", new Delta(requestId, part)));
        }
    }

    private OffsetDateTime now() {
        Instant instant = clock.instant();
        return OffsetDateTime.ofInstant(instant, JST).withNano((instant.getNano() / 1_000_000) * 1_000_000);
    }
    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ConversationSendException problem(String code, int status, String detail) { return new ConversationSendException(code, status, detail); }
    private static String safeMessage(String code) {
        return switch (code) {
            case "REQUEST_INTERRUPTED" -> "応答生成中に処理が中断されました。";
            case "RESPONSE_TIMEOUT" -> "Aliceの応答がタイムアウトしました。";
            case "MESSAGE_SAVE_FAILED" -> "Aliceの応答を保存できませんでした。";
            default -> "Aliceの応答を生成できませんでした。";
        };
    }

    public record PreparedSend(String key, String content, String requestId, IdempotencyState state, boolean newExecution) { }
    public record StreamEvent(String name, Object data) { }
    public record Started(String requestId, ConversationMessage userMessage) { }
    public record Delta(String requestId, String delta) { }
    public record Completed(String requestId, ConversationMessage userMessage, ConversationMessage assistantMessage) { }
    public record Failed(String requestId, String code, String message) { }

    public static final class ExecutionCancellation implements AiCancellationToken {
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.CopyOnWriteArrayList<Runnable> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
        private boolean terminal;
        @Override public boolean isCancellationRequested() { return cancelled.get(); }
        @Override public AutoCloseable onCancellation(Runnable callback) {
            if (cancelled.get()) callback.run();
            else { callbacks.add(callback); if (cancelled.get() && callbacks.remove(callback)) callback.run(); }
            return () -> callbacks.remove(callback);
        }
        public void cancel() { if (cancelled.compareAndSet(false, true)) callbacks.forEach(Runnable::run); }
        synchronized boolean terminal() { return terminal; }
        synchronized void markTerminal() { terminal = true; }
    }
}
