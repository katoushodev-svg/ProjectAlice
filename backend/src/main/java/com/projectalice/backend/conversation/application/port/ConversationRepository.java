package com.projectalice.backend.conversation.application.port;

import com.projectalice.backend.conversation.domain.ConversationMessage;
import java.util.List;
import java.util.Optional;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.OffsetDateTime;

/**
 * Persistence boundary for the Phase 1 conversation aggregate, including history reads and
 * the atomic persistence steps that coordinate a logical send with its idempotency state.
 * Implementations provide every operation so an incomplete adapter fails at compile time.
 */
public interface ConversationRepository {

    /**
     * Returns up to {@code limit} latest messages of the single conversation,
     * ordered by sequence descending, using a strongly consistent read.
     */
    List<ConversationMessage> findLatestMessages(int limit);

    /** Returns at most {@code limit} messages older than exclusiveStartSequence, newest first. */
    List<ConversationMessage> findMessagesBefore(long exclusiveStartSequence, int limit);

    Optional<ConversationState> findConversation();

    Optional<IdempotencyState> findIdempotency(String key);

    /** Atomically persists the initial user message and reserves the conversation for this send. */
    void start(SendStart start);

    /** Atomically persists the assistant message, completes idempotency, and releases the reservation. */
    void complete(String key, ConversationMessage assistantMessage);

    /** Atomically marks the send failed and releases its reservation. */
    void fail(String key, String failureCode, OffsetDateTime failedAt, long expiresAtEpochSeconds);

    /** Recovers an expired send using the established interrupted-request failure outcome. */
    void recoverExpired(String key, OffsetDateTime failedAt, long expiresAtEpochSeconds);

    record ConversationState(String id, OffsetDateTime createdAt, OffsetDateTime updatedAt, long lastSequence,
                             String activeIdempotencyKey, long busyLeaseExpiresAtEpochSeconds) { }

    record IdempotencyState(String key, String contentHash, String state, ConversationMessage userMessage,
                            ConversationMessage assistantMessage, String failureCode, long leaseExpiresAtEpochSeconds,
                            long expiresAtEpochSeconds) { }

    record SendStart(String key, String contentHash, ConversationMessage userMessage, OffsetDateTime now,
                     long leaseExpiresAtEpochSeconds, String conversationId) { }
}
