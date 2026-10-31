package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/** Conditional, read-back-verified ciphertext staging. Cleanup is scoped to the unique stage owner. */
public final class DynamoDbRelationReviewStageStore implements RelationReviewStageStore {
    private static final int MAX_ITEM_BYTES = 32 * 1024;
    private final DynamoDbClient client;
    private final String tableName;
    private final RelationTargetSetDigester digester;

    public DynamoDbRelationReviewStageStore(DynamoDbClient client, String tableName,
            RelationTargetSetDigester digester) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.digester = Objects.requireNonNull(digester, "digester must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    @Override public void createPreparingMetadata(String reviewId, long reviewVersion, int keyVersion,
            RelationReviewDraft draft, String targetSetDigest, String stageOwner,
            OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", s(reviewPk(reviewId))); item.put("sk", s("META"));
        item.put("itemType", s("MEMORY_RELATION_REVIEW")); item.put("schemaVersion", n(1));
        item.put("relationReviewId", s(reviewId)); item.put("reviewVersion", n(reviewVersion));
        item.put("status", s("PREPARING")); item.put("sourceOperation", s("REGISTER"));
        item.put("relationType", s(draft.relationType().name())); item.put("stateKeyVersion", n(keyVersion));
        item.put("targetCount", n(draft.targets().size())); item.put("targetSetDigest", s(targetSetDigest));
        item.put("candidateCiphertextSk", s("CANDIDATE")); item.put("stageOwner", s(stageOwner));
        item.put("createdAt", s(createdAt.toString())); item.put("updatedAt", s(createdAt.toString()));
        item.put("reviewExpiresAt", s(expiresAt.toString()));
        item.put("expiresAtEpochSeconds", n(expiresAt.toEpochSecond()));
        item.put("cleanupState", s("NOT_STARTED")); item.put("cleanupEpoch", n(0));
        item.put("activeSlotReleased", bool(false));
        putConditional(item);
    }

    @Override public void stageAndVerify(String reviewId, long reviewVersion, int keyVersion, String stageOwner,
            RelationCiphertext candidate, List<EncryptedTarget> targets, String expectedTargetSetDigest) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        List<EncryptedTarget> immutable = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
        if (immutable.isEmpty() || immutable.size() > 100) throw new IllegalArgumentException("target count must be 1..100");
        if (candidate.keyVersion() != keyVersion || immutable.stream().anyMatch(t -> t.ciphertext().keyVersion() != keyVersion))
            throw new RelationCryptoException();
        putConditional(candidateItem(reviewId, reviewVersion, keyVersion, stageOwner, candidate));
        for (EncryptedTarget target : immutable) {
            putConditional(targetItem(reviewId, reviewVersion, keyVersion, stageOwner, target));
        }
        verifyReadBack(reviewId, reviewVersion, keyVersion, stageOwner, immutable, expectedTargetSetDigest);
    }

    private void verifyReadBack(String reviewId, long reviewVersion, int keyVersion, String stageOwner, List<EncryptedTarget> expected,
            String expectedDigest) {
        List<Map<String, AttributeValue>> queriedItems = queryAll(reviewId);
        List<RelationTargetSetDigester.TargetIdentity> actual = new ArrayList<>();
        boolean candidateSeen = false;
        for (Map<String, AttributeValue> item : queriedItems) {
            String sk = string(item, "sk");
            if (sk.equals("CANDIDATE")) {
                if (!stageOwner.equals(string(item, "stageOwner"))
                        || !"STAGED".equals(string(item, "ciphertextState"))
                        || number(item, "reviewVersion") != reviewVersion
                        || number(item, "stateKeyVersion") != keyVersion
                        || !reviewId.equals(string(item, "relationReviewId"))) throw new RelationCryptoException();
                candidateSeen = true;
                continue;
            }
            if (!sk.startsWith("TARGET#")) continue;
            if (!stageOwner.equals(string(item, "stageOwner"))
                    || !"STAGED".equals(string(item, "ciphertextState"))
                    || number(item, "reviewVersion") != reviewVersion
                    || number(item, "stateKeyVersion") != keyVersion
                    || !reviewId.equals(string(item, "relationReviewId"))
                    || !"UPDATE_TARGET".equals(string(item, "allowedMutation")))
                throw new RelationCryptoException();
            int ordinal = Integer.parseInt(sk.substring("TARGET#".length()));
            if (number(item, "targetOrdinal") != ordinal) throw new RelationCryptoException();
            actual.add(new RelationTargetSetDigester.TargetIdentity(ordinal, string(item, "targetMemoryId"),
                    number(item, "expectedTargetVersion")));
        }
        if (!candidateSeen || actual.size() != expected.size()) throw new RelationCryptoException();
        String actualDigest = digester.digestTargets(actual);
        if (!java.security.MessageDigest.isEqual(actualDigest.getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII))) throw new RelationCryptoException();
    }

    @Override public void cleanupOwnedCiphertext(String reviewId, String stageOwner) {
        List<Map<String, AttributeValue>> queriedItems = queryAll(reviewId);
        for (Map<String, AttributeValue> item : queriedItems) {
            String sk = string(item, "sk");
            if (!sk.equals("CANDIDATE") && !sk.startsWith("TARGET#")) continue;
            if (!stageOwner.equals(optionalString(item, "stageOwner"))) continue;
            client.deleteItem(DeleteItemRequest.builder().tableName(tableName)
                    .key(Map.of("pk", s(reviewPk(reviewId)), "sk", s(sk)))
                    .conditionExpression("#stageOwner = :owner AND #ciphertextState = :staged")
                    .expressionAttributeNames(Map.of("#stageOwner", "stageOwner", "#ciphertextState", "ciphertextState"))
                    .expressionAttributeValues(Map.of(":owner", s(stageOwner), ":staged", s("STAGED"))).build());
        }
    }

    @Override public void markCleanupPending(String reviewId, String stageOwner, OffsetDateTime updatedAt) {
        client.updateItem(UpdateItemRequest.builder().tableName(tableName)
                .key(Map.of("pk", s(reviewPk(reviewId)), "sk", s("META")))
                .updateExpression("SET #status = :cleanup, #cleanupState = :pending, #updatedAt = :updatedAt, #cleanupEpoch = #cleanupEpoch + :one")
                .conditionExpression("#stageOwner = :owner AND (#status = :preparing OR #status = :cleanup)")
                .expressionAttributeNames(Map.of("#status", "status", "#cleanupState", "cleanupState",
                        "#updatedAt", "updatedAt", "#cleanupEpoch", "cleanupEpoch", "#stageOwner", "stageOwner"))
                .expressionAttributeValues(Map.of(":cleanup", s("CONTENT_CLEANUP_PENDING"), ":pending", s("CONTENT_CLEANUP_PENDING"),
                        ":updatedAt", s(updatedAt.toString()), ":one", n(1), ":owner", s(stageOwner), ":preparing", s("PREPARING"))).build());
    }

    Map<String, AttributeValue> candidateItem(String reviewId, long reviewVersion, int keyVersion,
            String stageOwner, RelationCiphertext ciphertext) {
        Map<String, AttributeValue> item = baseCipherItem(reviewId, reviewVersion, keyVersion, stageOwner, ciphertext);
        item.put("sk", s("CANDIDATE")); item.put("payloadKind", s("CANDIDATE"));
        return checked(item);
    }

    Map<String, AttributeValue> targetItem(String reviewId, long reviewVersion, int keyVersion,
            String stageOwner, EncryptedTarget target) {
        Map<String, AttributeValue> item = baseCipherItem(reviewId, reviewVersion, keyVersion, stageOwner, target.ciphertext());
        item.put("sk", s(String.format(java.util.Locale.ROOT, "TARGET#%03d", target.ordinal())));
        item.put("payloadKind", s("TARGET_PREVIEW")); item.put("targetOrdinal", n(target.ordinal()));
        item.put("targetMemoryId", s(target.memoryId())); item.put("expectedTargetVersion", n(target.expectedVersion()));
        item.put("allowedMutation", s(target.allowedMutation()));
        return checked(item);
    }

    private Map<String, AttributeValue> baseCipherItem(String reviewId, long reviewVersion, int keyVersion,
            String stageOwner, RelationCiphertext envelope) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", s(reviewPk(reviewId))); item.put("itemType", s("MEMORY_RELATION_REVIEW_CIPHERTEXT"));
        item.put("schemaVersion", n(1)); item.put("relationReviewId", s(reviewId));
        item.put("reviewVersion", n(reviewVersion)); item.put("stateKeyVersion", n(keyVersion));
        item.put("aadSchemaVersion", n(envelope.aadSchemaVersion()));
        item.put("nonce", s(Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.nonce())));
        item.put("ciphertext", s(Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.ciphertext())));
        if (stageOwner != null) item.put("stageOwner", s(stageOwner));
        item.put("ciphertextState", s(stageOwner == null ? "ACTIVE" : "STAGED"));
        return item;
    }

    private List<Map<String, AttributeValue>> queryAll(String reviewId) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> startKey = Map.of();
        do {
            var builder = QueryRequest.builder().tableName(tableName).consistentRead(true)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "pk"))
                    .expressionAttributeValues(Map.of(":pk", s(reviewPk(reviewId))));
            if (!startKey.isEmpty()) builder.exclusiveStartKey(startKey);
            var response = client.query(builder.build());
            if (response.items() != null) items.addAll(response.items());
            startKey = response.lastEvaluatedKey() == null ? Map.of() : response.lastEvaluatedKey();
        } while (!startKey.isEmpty());
        return List.copyOf(items);
    }

    private void putConditional(Map<String, AttributeValue> item) {
        if (item.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ITEM_BYTES)
            throw new IllegalArgumentException("Relation ciphertext item exceeds 32 KiB estimate");
        client.putItem(PutItemRequest.builder().tableName(tableName).item(Map.copyOf(item))
                .conditionExpression("attribute_not_exists(pk)").build());
    }

    private static Map<String, AttributeValue> checked(Map<String, AttributeValue> item) { return item; }
    private static String reviewPk(String id) {
        try { return "MEMORY_RELATION_REVIEW#" + java.util.UUID.fromString(id); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("reviewId must be a UUID"); }
    }
    private static String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name); if (value == null || value.s() == null) throw new RelationCryptoException(); return value.s();
    }
    private static String optionalString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name); return value == null || value.s() == null ? null : value.s();
    }
    private static long number(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name); if (value == null || value.n() == null) throw new RelationCryptoException(); return Long.parseLong(value.n());
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static AttributeValue bool(boolean value) { return AttributeValue.builder().bool(value).build(); }
}
