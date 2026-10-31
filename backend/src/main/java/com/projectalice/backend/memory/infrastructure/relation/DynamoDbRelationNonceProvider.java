package com.projectalice.backend.memory.infrastructure.relation;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Reserves a monotonically increasing per-key nonce counter in Relation Key Control.
 * Counter reservations are atomic and are intentionally consumed even if later encryption fails.
 */
public final class DynamoDbRelationNonceProvider implements RelationNonceProvider {
    public static final String KEY_CONTROL_PK = "MEMORY_RELATION_KEY#CONTROL";
    public static final String KEY_CONTROL_SK = "STATE";
    private static final long MAX_COUNTER = Long.MAX_VALUE;
    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbRelationNonceProvider(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        if (tableName.isBlank()) throw new IllegalArgumentException("tableName must not be blank");
    }

    @Override public byte[] reserveNonce(int keyVersion) {
        if (keyVersion < 1) throw new IllegalArgumentException("keyVersion must be positive");
        String version = Integer.toString(keyVersion);
        try {
            var response = client.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("pk", s(KEY_CONTROL_PK), "sk", s(KEY_CONTROL_SK)))
                    .updateExpression("SET #versions.#v.#counter = if_not_exists(#versions.#v.#counter, :zero) + :one, "
                            + "#controlVersion = #controlVersion + :one")
                    .conditionExpression("#itemType = :itemType AND #versions.#v.#state = :writeActive "
                            + "AND #controlVersion < :max "
                            + "AND attribute_exists(#versions.#v.#noncePrefix) "
                            + "AND (attribute_not_exists(#versions.#v.#counter) OR #versions.#v.#counter < :max)")
                    .expressionAttributeNames(Map.of(
                            "#itemType", "itemType", "#controlVersion", "controlVersion",
                            "#versions", "versions", "#v", version,
                            "#counter", "nextNonceCounter", "#state", "state", "#noncePrefix", "noncePrefix"))
                    .expressionAttributeValues(Map.of(
                            ":itemType", s("MEMORY_RELATION_KEY_CONTROL"), ":writeActive", s("WRITE_ACTIVE"),
                            ":zero", n("0"), ":one", n("1"), ":max", n(Long.toString(MAX_COUNTER))))
                    .returnValues(ReturnValue.ALL_NEW)
                    .build());
            Map<String, AttributeValue> versionItem = response.attributes().get("versions").m().get(version).m();
            byte[] prefix = Base64.getUrlDecoder().decode(versionItem.get("noncePrefix").s());
            long counter = Long.parseLong(versionItem.get("nextNonceCounter").n());
            if (prefix.length != 4 || counter < 1) throw new RelationCryptoException();
            return ByteBuffer.allocate(RelationCiphertext.NONCE_BYTES).put(prefix).putLong(counter).array();
        } catch (RelationCryptoException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new RelationCryptoException();
        }
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(String value) { return AttributeValue.builder().n(value).build(); }
}
