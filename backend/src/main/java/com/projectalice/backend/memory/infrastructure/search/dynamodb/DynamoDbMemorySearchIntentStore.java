package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

/** Conditional claim, takeover, retry and activation operations for projection intents. */
public final class DynamoDbMemorySearchIntentStore {
    public static final long LEASE_MILLIS = 300_000L;
    private static final String PENDING_PK = "MEMORY_SEARCH#PENDING";
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbMemorySearchIntentStore(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    /** Strongly checks the complete pending-intent partition; deferred retries and live claims still count. */
    public boolean hasPendingIntents() {
        var response = client.query(QueryRequest.builder().tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", "pk"))
                .expressionAttributeValues(Map.of(":pk", s(PENDING_PK)))
                .limit(1).consistentRead(true).build());
        return !response.items().isEmpty();
    }

    public List<Map<String, AttributeValue>> findAvailable(long nowEpochMillis, int limit) {
        if (nowEpochMillis < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid intent query bounds");
        }
        var response = client.query(QueryRequest.builder().tableName(tableName)
                .keyConditionExpression("#pk = :pk AND #sk BETWEEN :low AND :high")
                .expressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
                .expressionAttributeValues(Map.of(
                        ":pk", s(PENDING_PK), ":low", s("AVAILABLE#0000000000000"),
                        ":high", s("AVAILABLE#%013d~".formatted(nowEpochMillis))))
                .limit(limit).consistentRead(true).build());
        return response.items().stream().map(Map::copyOf).toList();
    }

    public List<Map<String, AttributeValue>> findTakeovers(long nowEpochMillis, int limit) {
        if (nowEpochMillis < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid intent query bounds");
        }
        var response = client.query(QueryRequest.builder().tableName(tableName)
                .keyConditionExpression("#pk = :pk AND #sk BETWEEN :low AND :high")
                .expressionAttributeNames(Map.of("#pk", "pk", "#sk", "sk"))
                .expressionAttributeValues(Map.of(
                        ":pk", s(PENDING_PK), ":low", s("CLAIMED#0000000000000"),
                        ":high", s("CLAIMED#%013d~".formatted(nowEpochMillis))))
                .limit(limit).consistentRead(true).build());
        return response.items().stream().map(Map::copyOf).toList();
    }

    public Claim claimAvailable(Map<String, AttributeValue> available, String ownerId, long nowEpochMillis) {
        return moveClaim(available, ownerId, nowEpochMillis, false);
    }

    public Claim takeOver(Map<String, AttributeValue> expiredClaim, String ownerId, long nowEpochMillis) {
        return moveClaim(expiredClaim, ownerId, nowEpochMillis, true);
    }

    private Claim moveClaim(Map<String, AttributeValue> source, String ownerId, long now, boolean takeover) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        if (!ownerId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("ownerId must be a canonical UUID v4");
        if (now < 0 || now > Long.MAX_VALUE - LEASE_MILLIS) throw new IllegalArgumentException("invalid time");
        String oldSk = requiredS(source, "sk");
        String intentId = requiredS(source, "intentId");
        long attempt = requiredN(source, "attemptCount");
        long epoch = takeover ? requiredN(source, "claimEpoch") : numberOr(source, "claimEpoch", 0);
        if (epoch == Long.MAX_VALUE) throw new IllegalStateException("claim epoch exhausted");
        long expires = now + LEASE_MILLIS;
        String newSk = claimedSortKey(expires, requiredS(source, "createdAt"),
                requiredS(source, "memoryId"), requiredN(source, "targetVersion"));
        Map<String, AttributeValue> claimed = new HashMap<>(source);
        claimed.put("sk", s(newSk));
        claimed.put("claimOwnerId", s(ownerId));
        claimed.put("claimEpoch", n(epoch + 1));
        claimed.put("leaseExpiresAtEpochMillis", n(expires));
        claimed.put("status", s("CLAIMED"));

        String condition = takeover
                ? "intentId = :intent AND claimOwnerId = :oldOwner AND claimEpoch = :oldEpoch "
                        + "AND leaseExpiresAtEpochMillis = :oldExpiry AND leaseExpiresAtEpochMillis <= :now"
                : "intentId = :intent AND attemptCount = :attempt AND attribute_not_exists(claimOwnerId)";
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":intent", s(intentId));
        if (takeover) {
            values.put(":now", n(now));
            values.put(":oldOwner", s(requiredS(source, "claimOwnerId")));
            values.put(":oldEpoch", n(epoch));
            values.put(":oldExpiry", n(requiredN(source, "leaseExpiresAtEpochMillis")));
        } else {
            values.put(":attempt", n(attempt));
        }
        Delete delete = Delete.builder().tableName(tableName).key(Map.of("pk", source.get("pk"), "sk", source.get("sk")))
                .conditionExpression(condition).expressionAttributeValues(Map.copyOf(values)).build();
        Put put = Put.builder().tableName(tableName).item(Map.copyOf(claimed))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().delete(delete).build(), TransactWriteItem.builder().put(put).build())).build());
        return new Claim(claimed, ownerId, epoch + 1, expires);
    }

    public Claim renew(Claim claim, long nowEpochMillis) {
        Objects.requireNonNull(claim, "claim must not be null");
        if (nowEpochMillis < 0 || nowEpochMillis > Long.MAX_VALUE - LEASE_MILLIS)
            throw new IllegalArgumentException("invalid time");
        long expiry = nowEpochMillis + LEASE_MILLIS;
        if (expiry <= claim.leaseExpiresAtEpochMillis())
            throw new IllegalArgumentException("renewal must extend the current lease");
        Map<String, AttributeValue> renewed = new HashMap<>(claim.item());
        renewed.put("leaseExpiresAtEpochMillis", n(expiry));
        renewed.put("sk", s(claimedSortKey(expiry, requiredS(claim.item(), "createdAt"),
                requiredS(claim.item(), "memoryId"), requiredN(claim.item(), "targetVersion"))));
        Delete delete = Delete.builder().tableName(tableName)
                .key(Map.of("pk", claim.item().get("pk"), "sk", claim.item().get("sk")))
                .conditionExpression("claimOwnerId = :owner AND claimEpoch = :epoch "
                        + "AND leaseExpiresAtEpochMillis = :expiry AND leaseExpiresAtEpochMillis > :now")
                .expressionAttributeValues(Map.of(":owner", s(claim.ownerId()), ":epoch", n(claim.epoch()),
                        ":expiry", n(claim.leaseExpiresAtEpochMillis()), ":now", n(nowEpochMillis))).build();
        Put put = Put.builder().tableName(tableName).item(Map.copyOf(renewed))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().delete(delete).build(), TransactWriteItem.builder().put(put).build())).build());
        return new Claim(renewed, claim.ownerId(), claim.epoch(), expiry);
    }

    public void retry(Claim claim, long nextAttemptAtEpochMillis, long nowEpochMillis) {
        Objects.requireNonNull(claim, "claim must not be null");
        if (nextAttemptAtEpochMillis <= nowEpochMillis || nowEpochMillis < 0)
            throw new IllegalArgumentException("retry time must be in the future");
        long attemptCount = requiredN(claim.item(), "attemptCount");
        if (attemptCount == Long.MAX_VALUE) throw new IllegalStateException("attempt count exhausted");
        long nextAttempt = attemptCount + 1;
        String memoryId = requiredS(claim.item(), "memoryId");
        long targetVersion = requiredN(claim.item(), "targetVersion");
        long createdAt = java.time.OffsetDateTime.parse(requiredS(claim.item(), "createdAt")).toInstant().toEpochMilli();
        Map<String, AttributeValue> available = new HashMap<>(claim.item());
        available.put("sk", s("AVAILABLE#%013d#%013d#%s#%020d".formatted(
                nextAttemptAtEpochMillis, createdAt, memoryId, targetVersion)));
        available.put("attemptCount", n(nextAttempt));
        available.put("nextAttemptAt", n(nextAttemptAtEpochMillis));
        available.remove("claimOwnerId");
        available.remove("leaseExpiresAtEpochMillis");
        available.remove("status");
        Delete delete = Delete.builder().tableName(tableName)
                .key(Map.of("pk", claim.item().get("pk"), "sk", claim.item().get("sk")))
                .conditionExpression("intentId = :intent AND claimOwnerId = :owner AND claimEpoch = :epoch "
                        + "AND leaseExpiresAtEpochMillis = :expiry AND leaseExpiresAtEpochMillis > :now")
                .expressionAttributeValues(Map.of(":intent", claim.item().get("intentId"),
                        ":owner", s(claim.ownerId()), ":epoch", n(claim.epoch()),
                        ":expiry", n(claim.leaseExpiresAtEpochMillis()), ":now", n(nowEpochMillis))).build();
        Put put = Put.builder().tableName(tableName).item(Map.copyOf(available))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().delete(delete).build(), TransactWriteItem.builder().put(put).build())).build());
    }

    public void activate(Claim claim, Map<String, AttributeValue> metadata, long nowEpochMillis) {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        long target = requiredN(claim.item(), "targetVersion");
        Put metadataPut = Put.builder().tableName(tableName).item(metadata)
                .conditionExpression("attribute_not_exists(activeSemanticVersion) OR activeSemanticVersion < :target")
                .expressionAttributeValues(Map.of(":target", n(target))).build();
        Delete intentDelete = Delete.builder().tableName(tableName)
                .key(Map.of("pk", claim.item().get("pk"), "sk", claim.item().get("sk")))
                .conditionExpression("intentId = :intent AND claimOwnerId = :owner AND claimEpoch = :epoch "
                        + "AND leaseExpiresAtEpochMillis = :expiry AND leaseExpiresAtEpochMillis > :now")
                .expressionAttributeValues(Map.of(":intent", claim.item().get("intentId"),
                        ":owner", s(claim.ownerId()), ":epoch", n(claim.epoch()),
                        ":expiry", n(claim.leaseExpiresAtEpochMillis()), ":now", n(nowEpochMillis))).build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().put(metadataPut).build(),
                TransactWriteItem.builder().delete(intentDelete).build())).build());
    }

    /** Removes a claimed intent already covered by the active version, without changing metadata. */
    public void completeSuperseded(Claim claim, long activeSemanticVersion, long nowEpochMillis) {
        Objects.requireNonNull(claim, "claim must not be null");
        if (activeSemanticVersion < requiredN(claim.item(), "targetVersion")) {
            throw new IllegalArgumentException("active version does not supersede the claimed intent");
        }
        String memoryId = requiredS(claim.item(), "memoryId");
        ConditionCheck activeCheck = ConditionCheck.builder().tableName(tableName)
                .key(Map.of("pk", s("MEMORY_SEARCH#PROJECTION#" + memoryId), "sk", s("META")))
                .conditionExpression("activeSemanticVersion = :active")
                .expressionAttributeValues(Map.of(":active", n(activeSemanticVersion))).build();
        Delete intentDelete = Delete.builder().tableName(tableName)
                .key(Map.of("pk", claim.item().get("pk"), "sk", claim.item().get("sk")))
                .conditionExpression("intentId = :intent AND targetVersion <= :active AND claimOwnerId = :owner "
                        + "AND claimEpoch = :epoch AND leaseExpiresAtEpochMillis = :expiry "
                        + "AND leaseExpiresAtEpochMillis > :now")
                .expressionAttributeValues(Map.of(":intent", claim.item().get("intentId"),
                        ":active", n(activeSemanticVersion), ":owner", s(claim.ownerId()),
                        ":epoch", n(claim.epoch()), ":expiry", n(claim.leaseExpiresAtEpochMillis()),
                        ":now", n(nowEpochMillis))).build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().conditionCheck(activeCheck).build(),
                TransactWriteItem.builder().delete(intentDelete).build())).build());
    }

    private static String claimedSortKey(long expires, String createdAt, String memoryId, long targetVersion) {
        return "CLAIMED#%013d#%013d#%s#%020d".formatted(expires,
                java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli(), memoryId, targetVersion);
    }
    private static String requiredS(Map<String, AttributeValue> map, String key) {
        AttributeValue v = map.get(key); if (v == null || v.s() == null) throw new IllegalArgumentException("missing " + key); return v.s();
    }
    private static long requiredN(Map<String, AttributeValue> map, String key) {
        AttributeValue v = map.get(key); if (v == null || v.n() == null) throw new IllegalArgumentException("missing " + key); return Long.parseLong(v.n());
    }
    private static long numberOr(Map<String, AttributeValue> map, String key, long fallback) {
        return map.containsKey(key) ? requiredN(map, key) : fallback;
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    public record Claim(Map<String, AttributeValue> item, String ownerId, long epoch, long leaseExpiresAtEpochMillis) {
        public Claim { item = Map.copyOf(item); Objects.requireNonNull(ownerId); }
    }
}
