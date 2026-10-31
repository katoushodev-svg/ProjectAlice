package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.projectalice.backend.memory.application.RelationMutationPreview;
import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.application.RelationType;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import com.projectalice.backend.memory.infrastructure.idempotency.DynamoDbMemoryIdempotencyBindingRequests;
import com.projectalice.backend.memory.infrastructure.idempotency.MemoryIdempotencyBinding;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.Update;

class DynamoDbRelationReviewPublishExecutorTest {
    private static final String TABLE = "memory";

    @Test
    void sendsReviewAndBindingTogetherInOneDynamoDbTransaction() {
        AtomicReference<TransactWriteItemsRequest> sent = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        DynamoDbClient client = (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("transactWriteItems")) {
                        calls.incrementAndGet();
                        sent.set((TransactWriteItemsRequest) args[0]);
                        return TransactWriteItemsResponse.builder().build();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var executor = new DynamoDbRelationReviewPublishExecutor(client,
                new DynamoDbRelationReviewPublishRequestBuilder());

        executor.publishDirect(draft(), actions());

        assertEquals(1, calls.get());
        assertEquals(8, sent.get().transactItems().size());
        assertTrue(sent.get().transactItems().stream().anyMatch(item -> item.update() != null
                && item.update().conditionExpression().contains("leaseEpoch")));
        assertTrue(sent.get().transactItems().stream().anyMatch(item -> item.put() != null
                && item.put().item().containsKey("status")
                && "ACTIVE".equals(item.put().item().get("status").s())));
    }

    @Test
    void rejectsMissingEncryptedTargetBeforeCallingDynamoDb() {
        AtomicInteger calls = new AtomicInteger();
        DynamoDbClient client = (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[] {DynamoDbClient.class}, (proxy, method, args) -> {
                    if (method.getName().equals("transactWriteItems")) calls.incrementAndGet();
                    throw new UnsupportedOperationException(method.getName());
                });
        var executor = new DynamoDbRelationReviewPublishExecutor(client,
                new DynamoDbRelationReviewPublishRequestBuilder());
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> missingTarget = actions().stream()
                .filter(action -> action.role() != DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT)
                .toList();

        assertThrows(IllegalArgumentException.class, () -> executor.publishDirect(draft(), missingTarget));
        assertEquals(0, calls.get());
    }

    private static RelationReviewDraft draft() {
        var id = new MemoryId("00000000-0000-4000-8000-000000000001");
        var snapshot = new RelatedMemorySearchResult.Candidate(id, 2, new MemoryContent("target"),
                MemoryCategory.PREFERENCE, CaptureType.EXPLICIT, SensitivityLevel.NORMAL, MemoryState.ACTIVE);
        var preview = new RelationMutationPreview(RelationMutationPreview.Operation.UPDATE_TARGET,
                RelationType.UNCERTAIN, id, 2, new MemoryContent("proposed"),
                MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL);
        return new RelationReviewDraft(RelationType.UNCERTAIN, new MemoryContent("proposed"),
                MemoryCategory.PREFERENCE, SensitivityLevel.NORMAL,
                List.of(new RelationReviewDraft.Target(snapshot, preview)));
    }

    private static List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions() {
        var roles = DynamoDbRelationReviewPublishRequestBuilder.Role.values();
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = new ArrayList<>();
        actions.add(update(roles[0], "global", "STATE", "SET activeCount = activeCount + :one",
                "activeCount < :max AND controlVersion = :v", Map.of(),
                Map.of(":one", n(1), ":max", n(20), ":v", n(1))));
        actions.add(update(roles[1], "key", "STATE",
                "SET activeReviewCount = activeReviewCount + :one, controlVersion = controlVersion + :one",
                "#state = :usable AND controlVersion = :v AND activeReviewCount >= :zero",
                Map.of("#state", "state"), Map.of(":one", n(1), ":v", n(1), ":zero", n(0), ":usable", s("WRITE_ACTIVE"))));
        actions.add(put(roles[2], "key#2", "REVIEW#review", Map.of("referenceState", s("ACTIVE"))));
        actions.add(put(roles[3], "review", "META", Map.of("status", s("ACTIVE"))));
        actions.add(put(roles[4], "review", "CANDIDATE", Map.of("ciphertext", s("encrypted"))));
        actions.add(put(roles[5], "review", "TARGET#0", Map.of("ciphertext", s("encrypted"))));
        actions.add(put(roles[6], "active", "REVIEW#review", Map.of("status", s("ACTIVE"))));
        var now = OffsetDateTime.parse("2026-10-29T00:00:00Z");
        var binding = new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER", "A".repeat(43)),
                "B".repeat(43), java.util.UUID.randomUUID(), MemoryIdempotencyBinding.Status.PROCESSING,
                1, "owner", now.plusMinutes(2), null, null, now, now, null);
        actions.add(new DynamoDbRelationReviewPublishRequestBuilder.Action(roles[7],
                DynamoDbMemoryIdempotencyBindingRequests.completeForReview(TABLE, binding,
                        "00000000-0000-4000-8000-000000000002", 1, "REVIEW_REQUIRED", now.plusSeconds(1))));
        return List.copyOf(actions);
    }

    private static DynamoDbRelationReviewPublishRequestBuilder.Action update(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, String pk, String sk, String expression,
            String condition, Map<String, String> names, Map<String, AttributeValue> values) {
        return new DynamoDbRelationReviewPublishRequestBuilder.Action(role,
                TransactWriteItem.builder().update(Update.builder().tableName(TABLE).key(key(pk, sk))
                        .updateExpression(expression).conditionExpression(condition)
                        .expressionAttributeNames(names).expressionAttributeValues(values).build()).build());
    }

    private static DynamoDbRelationReviewPublishRequestBuilder.Action put(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, String pk, String sk,
            Map<String, AttributeValue> fields) {
        var item = new java.util.HashMap<>(key(pk, sk));
        item.putAll(fields);
        return new DynamoDbRelationReviewPublishRequestBuilder.Action(role,
                TransactWriteItem.builder().put(Put.builder().tableName(TABLE).item(item)
                        .conditionExpression("attribute_not_exists(pk)").build()).build());
    }

    private static Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of("pk", s(pk), "sk", s(sk));
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
