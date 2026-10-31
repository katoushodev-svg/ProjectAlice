package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.projectalice.backend.memory.infrastructure.idempotency.DynamoDbMemoryIdempotencyBindingRequests;
import com.projectalice.backend.memory.infrastructure.idempotency.MemoryIdempotencyBinding;
import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.application.RelationMutationPreview;
import com.projectalice.backend.memory.application.RelationType;
import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

class DynamoDbRelationReviewPublishRequestBuilderTest {
    private static final String TABLE = "memory";
    private final DynamoDbRelationReviewPublishRequestBuilder builder = new DynamoDbRelationReviewPublishRequestBuilder();

    @Test void directPublishIncludesEveryRequiredActionInOneBoundedRequest() {
        var request = builder.buildDirect(actions(2));
        assertEquals(9, request.transactItems().size());
        assertEquals(1, request.transactItems().stream().filter(i -> i.update() != null
                && i.update().conditionExpression().contains("activeCount")).count());
        assertEquals(1, request.transactItems().stream().filter(i -> i.update() != null
                && i.update().conditionExpression().contains("leaseEpoch")).count());
    }

    @Test void preparedDraftMustMatchEncryptedTargetsInTheSinglePublishRequest() {
        var draft = draft(1);
        assertEquals(8, builder.buildDirect(draft, actions(1)).transactItems().size());
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(draft, actions(0)));
    }

    @Test void directPathRequiresStageAboveActionBudgetAndRejectsDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(actions(19)));
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> duplicate = new ArrayList<>(actions(0));
        duplicate.add(put(DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT,
                "review", "CANDIDATE", "attribute_not_exists(pk)"));
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(duplicate));
    }

    @Test void stagedPublishConditionsOnVerifiedTargetCountAndSetDigest() {
        var staged = actions(0).stream().filter(a -> a.role() != DynamoDbRelationReviewPublishRequestBuilder.Role.CANDIDATE_CIPHERTEXT
                && a.role() != DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT).toList();
        var names = Map.of("#status", "status", "#targetSetDigest", "targetSetDigest", "#targetCount", "targetCount");
        var values = Map.of(":preparing", s("PREPARING"), ":digest", s(digest()), ":count", n(19));
        var review = update(DynamoDbRelationReviewPublishRequestBuilder.Role.REVIEW_METADATA,
                "review", "META", "SET #status = :active", "#status = :preparing AND #targetSetDigest = :digest AND #targetCount = :count",
                names, values);
        staged = replace(staged, DynamoDbRelationReviewPublishRequestBuilder.Role.REVIEW_METADATA, review);
        var reference = update(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_REFERENCE,
                "key#2", "REVIEW#id", "SET #referenceState = :active", "#referenceState = :preparing AND #reviewVersion = :version",
                Map.of("#referenceState", "referenceState", "#reviewVersion", "reviewVersion"),
                Map.of(":active", s("ACTIVE"), ":preparing", s("PREPARING"), ":version", n(1)));
        staged = replace(staged, DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_REFERENCE, reference);
        var keyControl = update(DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_CONTROL,
                "key", "STATE", "SET activeReviewCount = activeReviewCount + :one, preparingReferenceCount = preparingReferenceCount - :one, controlVersion = controlVersion + :one",
                "#state = :usable AND controlVersion = :v AND activeReviewCount >= :zero AND preparingReferenceCount > :zero",
                Map.of("#state", "state"), Map.of(":one", n(1), ":v", n(1), ":zero", n(0), ":usable", s("DECRYPT_ONLY")));
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> publishActions = replace(staged,
                DynamoDbRelationReviewPublishRequestBuilder.Role.KEY_CONTROL, keyControl);

        assertEquals(6, builder.buildStaged(publishActions, 19, digest()).transactItems().size());
        assertThrows(IllegalArgumentException.class, () -> builder.buildStaged(publishActions, 20, digest()));
    }

    @Test void rejectsBindingWithoutOwnerEpochAndProcessingConditionsOrTooManyActions() {
        var invalid = actions(0).stream().map(action -> action.role()
                == DynamoDbRelationReviewPublishRequestBuilder.Role.IDEMPOTENCY_BINDING
                ? update(action.role(), "binding", "META", "SET #status = :done",
                        "#status = :processing AND #operationType = :operationType AND #requestDigest = :requestDigest AND #leaseEpoch = :epoch AND #leaseOwner = :owner",
                        Map.of("#status", "status", "#operationType", "operationType", "#requestDigest", "requestDigest",
                                "#leaseEpoch", "leaseEpoch", "#leaseOwner", "leaseOwner"),
                        Map.of(":processing", s("PROCESSING"), ":operationType", s("MEMORY_REGISTER"),
                                ":requestDigest", s("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"), ":epoch", n(1), ":owner", s("worker"))) : action).toList();
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(invalid));
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(actions(0).subList(0, 5)));
    }

    @Test void enforcesPerItemAndAggregateRequestSizeLimitsBeforeExecution() {
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> large = actions(18).stream().map(action ->
                action.role() == DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT
                        ? put(action.role(), "review", action.item().put().item().get("sk").s(),
                                "attribute_not_exists(pk)", Map.of("ciphertext", s("x".repeat(28 * 1024))))
                        : action).toList();
        assertThrows(IllegalArgumentException.class, () -> builder.buildDirect(large));
    }

    private static List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions(int targets) {
        var roles = DynamoDbRelationReviewPublishRequestBuilder.Role.values();
        List<DynamoDbRelationReviewPublishRequestBuilder.Action> actions = new ArrayList<>();
        actions.add(update(roles[0], "global", "STATE", "SET activeCount = activeCount + :one", "activeCount < :max AND controlVersion = :v", Map.of(), Map.of(":one", n(1), ":max", n(20), ":v", n(1))));
        actions.add(update(roles[1], "key", "STATE", "SET activeReviewCount = activeReviewCount + :one, controlVersion = controlVersion + :one",
                "#state = :writeActive AND controlVersion = :v AND activeReviewCount >= :zero",
                Map.of("#state", "state"), Map.of(":one", n(1), ":v", n(1), ":zero", n(0), ":writeActive", s("WRITE_ACTIVE"))));
        actions.add(put(roles[2], "key#2", "REVIEW#id", "attribute_not_exists(pk)", Map.of("referenceState", s("ACTIVE"))));
        actions.add(put(roles[3], "review", "META", "attribute_not_exists(pk)", Map.of("status", s("ACTIVE"))));
        actions.add(put(roles[4], "review", "CANDIDATE", "attribute_not_exists(pk)", Map.of("ciphertext", s("encrypted"))));
        for (int i = 0; i < targets; i++) actions.add(put(DynamoDbRelationReviewPublishRequestBuilder.Role.TARGET_CIPHERTEXT,
                "review", "TARGET#" + i, "attribute_not_exists(pk)", Map.of("ciphertext", s("encrypted"))));
        actions.add(put(roles[6], "active", "REVIEW#id", "attribute_not_exists(pk)", Map.of("status", s("ACTIVE"))));
        var binding = new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity("MEMORY_REGISTER",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", java.util.UUID.randomUUID(),
                MemoryIdempotencyBinding.Status.PROCESSING, 1, "worker",
                java.time.OffsetDateTime.parse("2026-10-29T00:00:00Z").plusMinutes(5), null, null,
                java.time.OffsetDateTime.parse("2026-10-29T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-10-29T00:00:00Z"), null);
        actions.add(new DynamoDbRelationReviewPublishRequestBuilder.Action(roles[7],
                DynamoDbMemoryIdempotencyBindingRequests.completeForReview(TABLE, binding,
                        java.util.UUID.randomUUID().toString(), 1, "REVIEW_REQUIRED",
                        java.time.OffsetDateTime.parse("2026-10-29T00:00:01Z"))));
        return actions;
    }
    private static List<DynamoDbRelationReviewPublishRequestBuilder.Action> replace(
            List<DynamoDbRelationReviewPublishRequestBuilder.Action> input,
            DynamoDbRelationReviewPublishRequestBuilder.Role role,
            DynamoDbRelationReviewPublishRequestBuilder.Action replacement) {
        return input.stream().map(a -> a.role() == role ? replacement : a).toList();
    }
    private static DynamoDbRelationReviewPublishRequestBuilder.Action update(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, String pk, String sk, String update,
            String condition, Map<String, String> names, Map<String, AttributeValue> values) {
        return new DynamoDbRelationReviewPublishRequestBuilder.Action(role,
                TransactWriteItem.builder().update(Update.builder().tableName(TABLE)
                        .key(key(pk, sk)).updateExpression(update).conditionExpression(condition)
                        .expressionAttributeNames(names).expressionAttributeValues(values).build()).build());
    }
    private static DynamoDbRelationReviewPublishRequestBuilder.Action put(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, String pk, String sk, String condition,
            Map<String, AttributeValue> extra) {
        Map<String, AttributeValue> item = new java.util.HashMap<>(key(pk, sk)); item.putAll(extra);
        return new DynamoDbRelationReviewPublishRequestBuilder.Action(role,
                TransactWriteItem.builder().put(Put.builder().tableName(TABLE).item(item)
                        .conditionExpression(condition).build()).build());
    }
    private static DynamoDbRelationReviewPublishRequestBuilder.Action put(
            DynamoDbRelationReviewPublishRequestBuilder.Role role, String pk, String sk, String condition) {
        return put(role, pk, sk, condition, Map.of());
    }
    private static Map<String, AttributeValue> key(String pk, String sk) { return Map.of("pk", s(pk), "sk", s(sk)); }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static String digest() { return "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"; }

    private static RelationReviewDraft draft(int count) {
        var content = new MemoryContent("review candidate");
        var category = MemoryCategory.PREFERENCE;
        var sensitivity = SensitivityLevel.NORMAL;
        List<RelationReviewDraft.Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var id = new MemoryId("00000000-0000-4000-8000-00000000000" + (i + 1));
            var snapshot = new RelatedMemorySearchResult.Candidate(id, 1, new MemoryContent("target " + i),
                    category, CaptureType.EXPLICIT, sensitivity, MemoryState.ACTIVE);
            var preview = new RelationMutationPreview(RelationMutationPreview.Operation.UPDATE_TARGET,
                    RelationType.UNCERTAIN, id, 1, content, category, sensitivity);
            targets.add(new RelationReviewDraft.Target(snapshot, preview));
        }
        return new RelationReviewDraft(RelationType.UNCERTAIN, content, category, sensitivity, targets);
    }
}
