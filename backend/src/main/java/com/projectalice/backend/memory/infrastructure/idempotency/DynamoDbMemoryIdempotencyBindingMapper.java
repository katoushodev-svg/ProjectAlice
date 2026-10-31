package com.projectalice.backend.memory.infrastructure.idempotency;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Maps the content-free §38.4 Binding schema. Does not log malformed or sensitive attributes. */
public final class DynamoDbMemoryIdempotencyBindingMapper {
    private static final int MAX_BINDING_ITEM_BYTES = 8 * 1024;
    private static final java.util.Set<String> SCHEMA_FIELDS = java.util.Set.of("pk", "sk", "itemType",
            "schemaVersion", "operationType", "requestDigest", "operationId", "status", "leaseEpoch",
            "leaseOwner", "leaseExpiresAt", "resultType", "resultReference", "safeOutcomeCode",
            "createdAt", "updatedAt", "expiresAtEpochSeconds");
    private DynamoDbMemoryIdempotencyBindingMapper() { }

    public static Map<String, AttributeValue> toItem(MemoryIdempotencyBinding binding) {
        Objects.requireNonNull(binding, "binding must not be null");
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", s(binding.identity().partitionKey()));
        item.put("sk", s(binding.identity().sortKey()));
        item.put("itemType", s("MEMORY_IDEMPOTENCY_BINDING"));
        item.put("schemaVersion", n(1));
        item.put("operationType", s(binding.identity().operationType()));
        item.put("requestDigest", s(binding.requestDigest()));
        item.put("operationId", s(binding.operationId().toString()));
        item.put("status", s(binding.status().name()));
        item.put("leaseEpoch", n(binding.leaseEpoch()));
        item.put("leaseOwner", s(binding.leaseOwner()));
        item.put("leaseExpiresAt", s(binding.leaseExpiresAt().toString()));
        item.put("resultType", nullableString(binding.resultReference() == null
                ? null : binding.resultReference().type().name()));
        item.put("resultReference", resultReference(binding.resultReference()));
        item.put("safeOutcomeCode", nullableString(binding.safeOutcomeCode()));
        item.put("createdAt", s(binding.createdAt().toString()));
        item.put("updatedAt", s(binding.updatedAt().toString()));
        item.put("expiresAtEpochSeconds", binding.expiresAtEpochSeconds() == null
                ? AttributeValue.builder().nul(true).build() : n(binding.expiresAtEpochSeconds()));
        Map<String, AttributeValue> mapped = Map.copyOf(item);
        if (mapped.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BINDING_ITEM_BYTES)
            throw new IllegalArgumentException("Binding item exceeds 8 KiB");
        return mapped;
    }

    public static MemoryIdempotencyBinding fromItem(Map<String, AttributeValue> item) {
        Objects.requireNonNull(item, "item must not be null");
        if (!item.keySet().equals(SCHEMA_FIELDS))
            throw new IllegalArgumentException("Binding record has missing or unsupported attributes");
        if (integer(item, "schemaVersion") != 1 || !"MEMORY_IDEMPOTENCY_BINDING".equals(string(item, "itemType")))
            throw new IllegalArgumentException("unsupported Binding record");
        String op = string(item, "operationType");
        String pk = string(item, "pk");
        String prefix = "MEMORY_OPERATION#IDEMPOTENCY#" + op + "#";
        if (!pk.startsWith(prefix) || !"META".equals(string(item, "sk")))
            throw new IllegalArgumentException("Binding key does not match its operation type");
        String keyDigest = pk.substring(prefix.length());
        String resultType = nullable(item, "resultType");
        var result = resultType == null ? null : readResult(item, resultType);
        return new MemoryIdempotencyBinding(new MemoryIdempotencyBinding.Identity(op, keyDigest),
                string(item, "requestDigest"), java.util.UUID.fromString(string(item, "operationId")),
                MemoryIdempotencyBinding.Status.valueOf(string(item, "status")), number(item, "leaseEpoch"),
                string(item, "leaseOwner"), java.time.OffsetDateTime.parse(string(item, "leaseExpiresAt")),
                result, nullable(item, "safeOutcomeCode"),
                java.time.OffsetDateTime.parse(string(item, "createdAt")),
                java.time.OffsetDateTime.parse(string(item, "updatedAt")), nullableLong(item, "expiresAtEpochSeconds"));
    }

    static AttributeValue resultReference(MemoryIdempotencyBinding.ResultReference reference) {
        if (reference == null) return AttributeValue.builder().nul(true).build();
        Map<String, AttributeValue> value = new HashMap<>();
        value.put("id", s(reference.id()));
        if (reference.version() != null) value.put("version", n(reference.version()));
        return AttributeValue.builder().m(Map.copyOf(value)).build();
    }
    private static MemoryIdempotencyBinding.ResultReference readResult(Map<String, AttributeValue> item,
            String type) {
        AttributeValue value = item.get("resultReference");
        if (value == null || value.m() == null) throw new IllegalArgumentException("invalid Binding result locator");
        String id = string(value.m(), "id");
        AttributeValue version = value.m().get("version");
        return new MemoryIdempotencyBinding.ResultReference(MemoryIdempotencyBinding.ResultType.valueOf(type), id,
                version == null ? null : Long.valueOf(version.n()));
    }
    private static AttributeValue nullableString(String value) {
        return value == null ? AttributeValue.builder().nul(true).build() : s(value);
    }
    private static String nullable(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || Boolean.TRUE.equals(value.nul()) ? null : value.s();
    }
    private static Long nullableLong(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || Boolean.TRUE.equals(value.nul()) ? null : Long.valueOf(value.n());
    }
    private static String string(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) throw new IllegalArgumentException("invalid Binding record");
        return value.s();
    }
    private static long number(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalArgumentException("invalid Binding record");
        return Long.parseLong(value.n());
    }
    private static int integer(Map<String, AttributeValue> item, String name) { return Math.toIntExact(number(item, name)); }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
