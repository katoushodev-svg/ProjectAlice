package com.projectalice.backend.memory.infrastructure.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class MemoryIdempotencyBindingMapperTest {
    private static final String DIGEST = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-29T00:00:00Z");

    @Test void contentFreeProcessingAndCompletedBindingsRoundTrip() {
        var identity = new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", DIGEST);
        var processing = new MemoryIdempotencyBinding(identity, DIGEST, UUID.randomUUID(),
                MemoryIdempotencyBinding.Status.PROCESSING, 1, "instance-1", NOW.plusMinutes(5),
                null, null, NOW, NOW, null);
        assertEquals(processing, DynamoDbMemoryIdempotencyBindingMapper.fromItem(
                DynamoDbMemoryIdempotencyBindingMapper.toItem(processing)));

        var completed = new MemoryIdempotencyBinding(identity, DIGEST, UUID.randomUUID(),
                MemoryIdempotencyBinding.Status.COMPLETED, 1, "instance-1", NOW.plusMinutes(5),
                new MemoryIdempotencyBinding.ResultReference(MemoryIdempotencyBinding.ResultType.REVIEW,
                        UUID.randomUUID().toString(), 1L), "REVIEW_REQUIRED", NOW, NOW, null);
        assertEquals(completed, DynamoDbMemoryIdempotencyBindingMapper.fromItem(
                DynamoDbMemoryIdempotencyBindingMapper.toItem(completed)));
        var withUnknown = new HashMap<>(DynamoDbMemoryIdempotencyBindingMapper.toItem(processing));
        withUnknown.put("candidate", AttributeValue.builder().s("must-not-be-bound").build());
        assertThrows(IllegalArgumentException.class, () -> DynamoDbMemoryIdempotencyBindingMapper.fromItem(withUnknown));
    }

    @Test void rejectsRawKeyShapeInvalidDigestsAndNonSafeProcessingResult() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", "raw-client-key"));
        assertFalse(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", DIGEST)
                .partitionKey().contains("raw-client-key"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryIdempotencyBinding(
                new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", DIGEST), "bad", UUID.randomUUID(),
                MemoryIdempotencyBinding.Status.PROCESSING, 1, "worker", NOW.plusMinutes(1), null,
                null, NOW, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryIdempotencyBinding(
                new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", DIGEST), DIGEST, UUID.randomUUID(),
                MemoryIdempotencyBinding.Status.PROCESSING, 1, "worker", NOW.plusMinutes(1),
                new MemoryIdempotencyBinding.ResultReference(MemoryIdempotencyBinding.ResultType.MEMORY,
                        UUID.randomUUID().toString(), 1L), null, NOW, NOW, null));
    }

    @Test void createsConditionalReservationAndOwnerEpochBoundReviewCompletionActions() {
        var processing = new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", DIGEST),
                DIGEST, UUID.randomUUID(), MemoryIdempotencyBinding.Status.PROCESSING, 3, "instance-2",
                NOW.plusMinutes(5), null, null, NOW, NOW, null);
        var reserve = DynamoDbMemoryIdempotencyBindingRequests.reservationPut("memory", processing);
        assertEquals("attribute_not_exists(pk)", reserve.put().conditionExpression());
        assertEquals("MEMORY_IDEMPOTENCY_BINDING", reserve.put().item().get("itemType").s());
        assertEquals(DIGEST, reserve.put().item().get("requestDigest").s());

        var completion = DynamoDbMemoryIdempotencyBindingRequests.completeForReview("memory", processing,
                UUID.randomUUID().toString(), 1, "REVIEW_REQUIRED", NOW.plusSeconds(1));
        assertEquals(2, completion.update().key().size());
        assertEquals("COMPLETED", completion.update().expressionAttributeValues().get(":completed").s());
        assertEquals("REVIEW", completion.update().expressionAttributeValues().get(":reviewType").s());
        assertEquals(3, Long.parseLong(completion.update().expressionAttributeValues().get(":leaseEpoch").n()));
        assertEquals("instance-2", completion.update().expressionAttributeValues().get(":leaseOwner").s());
        assertEquals("PROCESSING", completion.update().expressionAttributeValues().get(":processing").s());
        AttributeValue locator = completion.update().expressionAttributeValues().get(":resultReference");
        assertEquals(1L, Long.parseLong(locator.m().get("version").n()));
    }
}
