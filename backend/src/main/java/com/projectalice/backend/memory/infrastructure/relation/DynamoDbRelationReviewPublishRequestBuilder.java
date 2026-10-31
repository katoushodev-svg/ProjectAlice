package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

/**
 * Pure assembly and preflight validation for the single Relation Review publish transaction.
 * This class never executes DynamoDB calls; the caller must provide every required mutation.
 */
public final class DynamoDbRelationReviewPublishRequestBuilder {
    public static final int MAX_ACTIONS = 25;
    public static final int MAX_ESTIMATED_REQUEST_BYTES = 512 * 1024;
    public static final int MAX_TARGETS = 100;

    public enum Role {
        GLOBAL_CONTROL, KEY_CONTROL, KEY_REFERENCE, REVIEW_METADATA, CANDIDATE_CIPHERTEXT,
        TARGET_CIPHERTEXT, ACTIVE_DIRECTORY, IDEMPOTENCY_BINDING
    }

    public record Action(Role role, TransactWriteItem item) {
        public Action {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(item, "item must not be null");
        }
    }

    /**
     * Builds the non-staged path. More than 18 Related Targets must be staged first so the
     * controls, reference, metadata, candidate, directory and Binding still fit in 25 Actions.
     */
    public TransactWriteItemsRequest buildDirect(List<Action> actions) {
        List<Action> immutable = List.copyOf(Objects.requireNonNull(actions, "actions must not be null"));
        requireExactlyOne(immutable, Role.GLOBAL_CONTROL);
        requireExactlyOne(immutable, Role.KEY_CONTROL);
        requireExactlyOne(immutable, Role.KEY_REFERENCE);
        requireExactlyOne(immutable, Role.REVIEW_METADATA);
        requireExactlyOne(immutable, Role.CANDIDATE_CIPHERTEXT);
        requireExactlyOne(immutable, Role.ACTIVE_DIRECTORY);
        requireExactlyOne(immutable, Role.IDEMPOTENCY_BINDING);
        long targetCount = immutable.stream().filter(a -> a.role() == Role.TARGET_CIPHERTEXT).count();
        if (targetCount > 18) throw new IllegalArgumentException("targets must be staged before publish");
        requireActionKindsAndConditions(immutable, false);
        return assemble(immutable);
    }

    /** Validates that the encrypted direct-write actions cover every target in the prepared draft. */
    public TransactWriteItemsRequest buildDirect(RelationReviewDraft draft, List<Action> actions) {
        Objects.requireNonNull(draft, "draft must not be null");
        List<Action> immutable = List.copyOf(Objects.requireNonNull(actions, "actions must not be null"));
        long targetCount = immutable.stream().filter(a -> a.role() == Role.TARGET_CIPHERTEXT).count();
        if (targetCount != draft.targets().size())
            throw new IllegalArgumentException("encrypted target actions must match the prepared Review draft");
        return buildDirect(immutable);
    }

    /**
     * Builds the commit transaction after encrypted target staging and digest verification.
     * The Review metadata update must condition on the staged target count and set digest.
     */
    public TransactWriteItemsRequest buildStaged(List<Action> actions, int stagedTargetCount,
            String expectedTargetSetDigest) {
        if (stagedTargetCount < 19 || stagedTargetCount > MAX_TARGETS)
            throw new IllegalArgumentException("staged target count must be between 19 and 100");
        Objects.requireNonNull(expectedTargetSetDigest, "expectedTargetSetDigest must not be null");
        if (!expectedTargetSetDigest.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("target set digest must be a full SHA-256 base64url digest");
        List<Action> immutable = List.copyOf(Objects.requireNonNull(actions, "actions must not be null"));
        for (Role role : Role.values()) {
            if (role == Role.CANDIDATE_CIPHERTEXT || role == Role.TARGET_CIPHERTEXT) continue;
            requireExactlyOne(immutable, role);
        }
        if (immutable.stream().anyMatch(a -> a.role() == Role.CANDIDATE_CIPHERTEXT
                || a.role() == Role.TARGET_CIPHERTEXT))
            throw new IllegalArgumentException("staged ciphertext must not be rewritten during publish");
        requireActionKindsAndConditions(immutable, true);
        Action review = immutable.stream().filter(a -> a.role() == Role.REVIEW_METADATA).findFirst().orElseThrow();
        if (!references(review.item(), "targetSetDigest") || !references(review.item(), "targetCount")
                || !hasValue(review.item(), expectedTargetSetDigest)
                || !hasValue(review.item(), Integer.toString(stagedTargetCount)))
            throw new IllegalArgumentException("Review publish condition must bind staged target count and digest");
        if (!hasConditionValue(review.item(), expectedTargetSetDigest)
                || !hasConditionValue(review.item(), Integer.toString(stagedTargetCount)))
            throw new IllegalArgumentException("Review publish condition must compare the staged digest and count");
        return assemble(immutable);
    }

    /** Ensures the staged encrypted set represents every target from the prepared draft. */
    public TransactWriteItemsRequest buildStaged(RelationReviewDraft draft, List<Action> actions,
            int stagedTargetCount, String expectedTargetSetDigest) {
        Objects.requireNonNull(draft, "draft must not be null");
        if (stagedTargetCount != draft.targets().size())
            throw new IllegalArgumentException("staged target count must match the prepared Review draft");
        return buildStaged(actions, stagedTargetCount, expectedTargetSetDigest);
    }

    private static TransactWriteItemsRequest assemble(List<Action> actions) {
        if (actions.size() > MAX_ACTIONS) throw new IllegalArgumentException("transaction exceeds 25 Actions");
        Set<String> itemKeys = new HashSet<>();
        for (Action action : actions) {
            String itemKey = itemKey(action.item());
            if (!itemKeys.add(itemKey)) throw new IllegalArgumentException("duplicate DynamoDB item in transaction");
            int rowLimit = switch (action.role()) {
                case REVIEW_METADATA -> 16 * 1024;
                case CANDIDATE_CIPHERTEXT, TARGET_CIPHERTEXT -> 32 * 1024;
                case IDEMPOTENCY_BINDING -> 8 * 1024;
                default -> 8 * 1024;
            };
            if (action.item().toString().getBytes(StandardCharsets.UTF_8).length > rowLimit)
                throw new IllegalArgumentException("transaction row exceeds its design size limit");
        }
        List<TransactWriteItem> items = actions.stream().map(Action::item).toList();
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder().transactItems(items).build();
        // SDK model text is intentionally used as a conservative, non-logging preflight estimate.
        // It includes field names/wrappers that the compact DynamoDB JSON wire body omits.
        long estimate = request.toString().getBytes(StandardCharsets.UTF_8).length + 16_384L;
        if (estimate > MAX_ESTIMATED_REQUEST_BYTES)
            throw new IllegalArgumentException("estimated transaction request exceeds 512 KiB");
        return request;
    }

    private static void requireExactlyOne(List<Action> actions, Role role) {
        long count = actions.stream().filter(action -> action.role() == role).count();
        if (count != 1) throw new IllegalArgumentException("transaction requires exactly one " + role + " action");
    }

    private static void requireActionKindsAndConditions(List<Action> actions, boolean staged) {
        for (Action action : actions) {
            String condition = condition(action.item());
            if (condition.isBlank()) throw new IllegalArgumentException("every publish action must be conditional");
            switch (action.role()) {
                case GLOBAL_CONTROL, KEY_CONTROL, IDEMPOTENCY_BINDING -> require(action.item().update() != null, action.role());
                case ACTIVE_DIRECTORY, CANDIDATE_CIPHERTEXT, TARGET_CIPHERTEXT -> require(action.item().put() != null, action.role());
                case KEY_REFERENCE -> {
                    if (staged) require(action.item().update() != null, action.role());
                    else require(action.item().put() != null, action.role());
                }
                case REVIEW_METADATA -> {
                    if (staged) require(action.item().update() != null, action.role());
                    else require(action.item().put() != null, action.role());
                }
            }
            if (action.role() == Role.GLOBAL_CONTROL && !references(action.item(), "activeCount"))
                throw new IllegalArgumentException("Global Control condition must enforce the active quota");
            if (action.role() == Role.GLOBAL_CONTROL && !condition.contains("<"))
                throw new IllegalArgumentException("Global Control condition must enforce the active-count ceiling");
            if (action.role() == Role.GLOBAL_CONTROL && !hasValue(action.item(), "20"))
                throw new IllegalArgumentException("Global Control condition must preserve the limit of 20");
            if (action.role() == Role.GLOBAL_CONTROL && !hasConditionValue(action.item(), "20"))
                throw new IllegalArgumentException("Global Control condition must compare against the limit of 20");
            if (action.role() == Role.GLOBAL_CONTROL && !action.item().update().updateExpression().contains("activeCount"))
                throw new IllegalArgumentException("Global Control update must increment activeCount");
            if (action.role() == Role.KEY_CONTROL && (!references(action.item(), "controlVersion")
                    || !references(action.item(), "activeReviewCount")))
                throw new IllegalArgumentException("Key Control must condition and increment its active count");
            if (action.role() == Role.KEY_CONTROL && !action.item().update().updateExpression()
                    .contains("activeReviewCount"))
                throw new IllegalArgumentException("Key Control update must increment the active count");
            if (action.role() == Role.KEY_CONTROL && (!references(action.item(), "state")
                    || !(hasValue(action.item(), "WRITE_ACTIVE") || hasValue(action.item(), "DECRYPT_ONLY"))))
                throw new IllegalArgumentException("Key Control condition must keep the referenced key usable");
            if (action.role() == Role.KEY_CONTROL && !(hasConditionValue(action.item(), "WRITE_ACTIVE")
                    || hasConditionValue(action.item(), "DECRYPT_ONLY")))
                throw new IllegalArgumentException("Key Control condition must compare the key state");
            if (staged && action.role() == Role.KEY_CONTROL
                    && (!action.item().update().updateExpression().contains("preparingReferenceCount")
                    || !references(action.item(), "preparingReferenceCount")))
                throw new IllegalArgumentException("staged publish must move the Key Control preparing count");
            if (action.role() == Role.KEY_REFERENCE && !references(action.item(), "referenceState")
                    && !condition.contains("attribute_not_exists"))
                throw new IllegalArgumentException("Key Reference condition must enforce identity and state");
            if (staged && action.role() == Role.KEY_REFERENCE && !hasConditionValue(action.item(), "PREPARING"))
                throw new IllegalArgumentException("staged Key Reference condition must require PREPARING state");
            if (!staged && action.role() == Role.KEY_REFERENCE
                    && !"ACTIVE".equals(action.item().put().item().get("referenceState").s()))
                throw new IllegalArgumentException("direct Key Reference must be published as ACTIVE");
            if (!staged && action.role() == Role.REVIEW_METADATA
                    && !"ACTIVE".equals(action.item().put().item().get("status").s()))
                throw new IllegalArgumentException("direct Review Metadata must be published as ACTIVE");
            if (action.role() == Role.IDEMPOTENCY_BINDING) {
                String update = action.item().update().updateExpression();
                if (!update.contains("resultReference") || !update.contains("resultType")
                        || !update.contains("safeOutcomeCode") || !hasValue(action.item(), "REVIEW"))
                    throw new IllegalArgumentException("Review Binding completion must store a content-free locator");
            }
            if (action.role() == Role.IDEMPOTENCY_BINDING
                    && (!references(action.item(), "operationType") || !references(action.item(), "requestDigest")
                    || !references(action.item(), "leaseEpoch") || !references(action.item(), "leaseOwner")
                    || !condition.contains(":processing") || !hasValue(action.item(), "PROCESSING")))
                throw new IllegalArgumentException("Binding condition must enforce owner, epoch and PROCESSING state");
        }
    }

    private static String itemKey(TransactWriteItem item) {
        String table;
        java.util.Map<String, AttributeValue> key;
        if (item.put() != null) {
            table = item.put().tableName();
            key = item.put().item();
        } else if (item.update() != null) {
            table = item.update().tableName();
            key = item.update().key();
        } else if (item.delete() != null) {
            table = item.delete().tableName();
            key = item.delete().key();
        } else if (item.conditionCheck() != null) {
            table = item.conditionCheck().tableName();
            key = item.conditionCheck().key();
        } else throw new IllegalArgumentException("empty transaction action");
        if (table == null || key == null || !key.containsKey("pk") || !key.containsKey("sk"))
            throw new IllegalArgumentException("transaction action requires table, pk and sk");
        AttributeValue pk = key.get("pk");
        AttributeValue sk = key.get("sk");
        if (pk.s() == null || sk.s() == null) throw new IllegalArgumentException("pk and sk must be strings");
        return table + '\0' + pk.s() + '\0' + sk.s();
    }

    private static String condition(TransactWriteItem item) {
        if (item.put() != null) return Objects.toString(item.put().conditionExpression(), "");
        if (item.update() != null) return Objects.toString(item.update().conditionExpression(), "");
        if (item.delete() != null) return Objects.toString(item.delete().conditionExpression(), "");
        if (item.conditionCheck() != null) return Objects.toString(item.conditionCheck().conditionExpression(), "");
        return "";
    }

    private static boolean hasValue(TransactWriteItem item, String expected) {
        java.util.Map<String, AttributeValue> values = item.update() == null ? java.util.Map.of()
                : Objects.requireNonNullElse(item.update().expressionAttributeValues(), java.util.Map.of());
        return values.values().stream().anyMatch(value -> expected.equals(value.s()) || expected.equals(value.n()));
    }

    private static boolean hasConditionValue(TransactWriteItem item, String expected) {
        java.util.Map<String, AttributeValue> values = item.update() == null ? java.util.Map.of()
                : Objects.requireNonNullElse(item.update().expressionAttributeValues(), java.util.Map.of());
        String condition = condition(item);
        return values.entrySet().stream().anyMatch(entry -> condition.contains(entry.getKey())
                && (expected.equals(entry.getValue().s()) || expected.equals(entry.getValue().n())));
    }

    private static boolean references(TransactWriteItem item, String attribute) {
        String expression = condition(item);
        if (expression.contains(attribute)) return true;
        java.util.Map<String, String> names = item.update() == null ? java.util.Map.of()
                : Objects.requireNonNullElse(item.update().expressionAttributeNames(), java.util.Map.of());
        return names.containsValue(attribute) && names.keySet().stream().anyMatch(expression::contains);
    }

    private static void require(boolean condition, Role role) {
        if (!condition) throw new IllegalArgumentException("invalid DynamoDB action kind for " + role);
    }
}
