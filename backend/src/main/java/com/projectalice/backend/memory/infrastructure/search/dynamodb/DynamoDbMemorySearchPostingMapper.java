package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Maps content-free KW_V1 posting descriptors to the accepted DynamoDB item schema. */
public final class DynamoDbMemorySearchPostingMapper {

    private static final int SCHEMA_VERSION = 1;

    private DynamoDbMemorySearchPostingMapper() {}

    public static Map<String, AttributeValue> current(
            int keyVersion,
            String termDigest,
            MemoryId memoryId,
            long semanticVersion,
            KwV1Analyzer.TermKind termKind) {
        validateCommon(keyVersion, termDigest, memoryId, termKind);
        if (semanticVersion < 1) {
            throw new IllegalArgumentException("semanticVersion must be positive");
        }
        Map<String, AttributeValue> item = base(keyVersion, memoryId, termKind);
        item.put("pk", string("MEMORY_SEARCH#TERM#" + keyVersion + "#CURRENT#" + termDigest));
        item.put("sk", string("MEMORY#" + memoryId.value()));
        item.put("semanticVersion", number(semanticVersion));
        return Map.copyOf(item);
    }

    public static Map<String, AttributeValue> historical(
            int keyVersion,
            String termDigest,
            MemoryId memoryId,
            long revisionNumber,
            KwV1Analyzer.TermKind termKind) {
        validateCommon(keyVersion, termDigest, memoryId, termKind);
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be positive");
        }
        Map<String, AttributeValue> item = base(keyVersion, memoryId, termKind);
        item.put("pk", string("MEMORY_SEARCH#TERM#" + keyVersion + "#HISTORICAL#" + termDigest));
        item.put("sk", string("MEMORY#" + memoryId.value() + "#REVISION#%020d".formatted(revisionNumber)));
        item.put("revisionNumber", number(revisionNumber));
        return Map.copyOf(item);
    }

    private static Map<String, AttributeValue> base(
            int keyVersion,
            MemoryId memoryId,
            KwV1Analyzer.TermKind termKind) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("itemType", string("MEMORY_SEARCH_POSTING"));
        item.put("schemaVersion", number(SCHEMA_VERSION));
        item.put("memoryId", string(memoryId.value()));
        item.put("analyzerVersion", string(KwV1Analyzer.VERSION));
        item.put("tokenKeyVersion", number(keyVersion));
        item.put("termKind", string(termKind.name()));
        item.put("weightCode", number(termKind.weightCode()));
        return item;
    }

    private static void validateCommon(
            int keyVersion, String termDigest, MemoryId memoryId, KwV1Analyzer.TermKind termKind) {
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        Objects.requireNonNull(termDigest, "termDigest must not be null");
        if (!termDigest.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("termDigest must be an unpadded Base64url SHA-256 digest");
        }
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(termKind, "termKind must not be null");
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
