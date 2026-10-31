package com.projectalice.backend.memory.infrastructure.relation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Get;

/** Strong-read and atomic first-time initialization of Relation Global and Key Controls. */
public final class DynamoDbRelationControlRepository {
    public static final String GLOBAL_PK = "MEMORY_RELATION_REVIEW#CONTROL";
    public static final String KEY_PK = "MEMORY_RELATION_KEY#CONTROL";
    public static final String CONTROL_SK = "STATE";
    private final DynamoDbClient client;
    private final String tableName;
    private final Clock clock;
    private final SecureRandom random;

    public DynamoDbRelationControlRepository(DynamoDbClient client, String tableName, Clock clock) {
        this(client, tableName, clock, new SecureRandom());
    }

    DynamoDbRelationControlRepository(DynamoDbClient client, String tableName, Clock clock, SecureRandom random) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    /**
     * Installs content-free metadata only after the external Relation Key Provider has passed
     * its Relation-purpose capability probe. Never accepts or persists key material.
     */
    public boolean initializeAfterKeyProbe(RelationStateKeyProvider keyProvider) {
        Objects.requireNonNull(keyProvider, "keyProvider must not be null");
        final int writeKeyVersion;
        try {
            keyProvider.probeRelationPurpose();
            RelationStateKeyProvider.KeyMaterial active = keyProvider.activeEncryptionKey();
            if (active == null) throw new RelationControlUnavailableException();
            writeKeyVersion = active.version();
        } catch (RuntimeException failure) {
            throw new RelationControlUnavailableException();
        }
        if (writeKeyVersion < 1) throw new IllegalArgumentException("writeKeyVersion must be positive");
        byte[] noncePrefix = new byte[4];
        random.nextBytes(noncePrefix);
        String encodedPrefix = Base64.getUrlEncoder().withoutPadding().encodeToString(noncePrefix);
        String now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(9)).toString();
        Map<String, AttributeValue> global = Map.ofEntries(
                Map.entry("pk", s(GLOBAL_PK)), Map.entry("sk", s(CONTROL_SK)),
                Map.entry("itemType", s("MEMORY_RELATION_REVIEW_CONTROL")), Map.entry("schemaVersion", n(1)),
                Map.entry("activeCount", n(0)), Map.entry("maxActiveCount", n(20)),
                Map.entry("controlVersion", n(1)), Map.entry("updatedAt", s(now)));
        Map<String, AttributeValue> version = Map.ofEntries(
                Map.entry("state", s("WRITE_ACTIVE")), Map.entry("noncePrefix", s(encodedPrefix)),
                Map.entry("nextNonceCounter", n(0)), Map.entry("activeReviewCount", n(0)),
                Map.entry("preparingReferenceCount", n(0)), Map.entry("cleanupPendingCount", n(0)));
        Map<String, AttributeValue> key = Map.ofEntries(
                Map.entry("pk", s(KEY_PK)), Map.entry("sk", s(CONTROL_SK)),
                Map.entry("itemType", s("MEMORY_RELATION_KEY_CONTROL")), Map.entry("schemaVersion", n(1)),
                Map.entry("writeKeyVersion", n(writeKeyVersion)), Map.entry("maxActiveKeyVersions", n(2)),
                Map.entry("versions", AttributeValue.builder().m(Map.of(Integer.toString(writeKeyVersion),
                        AttributeValue.builder().m(version).build())).build()),
                Map.entry("controlVersion", n(1)), Map.entry("updatedAt", s(now)));
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                    put(global), put(key))).build());
            return true;
        } catch (TransactionCanceledException | ConditionalCheckFailedException conflict) {
            // A competing initializer may have won. Only a complete, valid pair is idempotent success.
            Controls controls = readControls();
            if (controls.keys().writeKeyVersion() != writeKeyVersion)
                throw new RelationControlUnavailableException();
            return false;
        } catch (RuntimeException unavailable) {
            throw new RelationControlUnavailableException();
        }
    }

    public Controls readControls() {
        try {
            var snapshot = client.transactGetItems(TransactGetItemsRequest.builder().transactItems(List.of(
                    controlRead(GLOBAL_PK), controlRead(KEY_PK))).build());
            if (snapshot.responses() == null || snapshot.responses().size() != 2)
                throw new IllegalStateException();
            Map<String, AttributeValue> global = snapshot.responses().get(0).item();
            Map<String, AttributeValue> key = snapshot.responses().get(1).item();
            if (global == null || global.isEmpty() || key == null || key.isEmpty())
                throw new IllegalStateException();
            if (integer(global, "schemaVersion") != 1 || integer(key, "schemaVersion") != 1)
                throw new IllegalStateException();
            RelationGlobalControl globalControl = new RelationGlobalControl(
                    integer(global, "activeCount"), integer(global, "maxActiveCount"), number(global, "controlVersion"));
            Map<String, AttributeValue> versions = requiredMap(key, "versions");
            List<RelationKeyControl.KeyVersion> keyVersions = versions.entrySet().stream().map(entry -> {
                Map<String, AttributeValue> item = entry.getValue().m();
                return new RelationKeyControl.KeyVersion(Integer.parseInt(entry.getKey()),
                        RelationKeyControl.KeyState.valueOf(string(item, "state")), string(item, "noncePrefix"),
                        number(item, "nextNonceCounter"), number(item, "activeReviewCount"),
                        number(item, "preparingReferenceCount"), number(item, "cleanupPendingCount"));
            }).toList();
            RelationKeyControl keyControl = new RelationKeyControl(integer(key, "writeKeyVersion"),
                    integer(key, "maxActiveKeyVersions"), number(key, "controlVersion"), keyVersions);
            if (!"MEMORY_RELATION_REVIEW_CONTROL".equals(string(global, "itemType"))
                    || !"MEMORY_RELATION_KEY_CONTROL".equals(string(key, "itemType")))
                throw new IllegalStateException();
            long activeReferences = keyControl.versions().stream()
                    .mapToLong(RelationKeyControl.KeyVersion::activeReviewCount).reduce(0, Math::addExact);
            if (activeReferences != globalControl.activeCount()) throw new IllegalStateException();
            return new Controls(globalControl, keyControl);
        } catch (RuntimeException failure) {
            if (failure instanceof RelationControlUnavailableException unavailable) throw unavailable;
            throw new RelationControlUnavailableException();
        }
    }

    private TransactGetItem controlRead(String pk) {
        return TransactGetItem.builder().get(Get.builder().tableName(tableName)
                .key(Map.of("pk", s(pk), "sk", s(CONTROL_SK))).build()).build();
    }


    private TransactWriteItem put(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tableName)
                .item(item).conditionExpression("attribute_not_exists(pk)").build()).build();
    }
    private static Map<String, AttributeValue> requiredMap(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.m() == null) throw new IllegalStateException();
        return value.m();
    }
    private static String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) throw new IllegalStateException();
        return value.s();
    }
    private static long number(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalStateException();
        return Long.parseLong(value.n());
    }
    private static int integer(Map<String, AttributeValue> item, String name) {
        return Math.toIntExact(number(item, name));
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    public record Controls(RelationGlobalControl global, RelationKeyControl keys) {
        public Controls { Objects.requireNonNull(global); Objects.requireNonNull(keys); }
    }
}
