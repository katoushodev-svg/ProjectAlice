package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import com.projectalice.backend.memory.infrastructure.search.MemoryProjectionDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Maps validated projection data to the metadata attributes used by activation. */
public final class DynamoDbMemorySearchProjectionMetadataMapper {

    private static final int SCHEMA_VERSION = 1;

    private DynamoDbMemorySearchProjectionMetadataMapper() {}

    /**
     * Builds the metadata item for the final activation step. Callers must only persist this item
     * after all versioned pages and postings have been written successfully.
     */
    public static Map<String, AttributeValue> activationAttributes(
            KwV1MemoryProjectionBuilder.Projection projection,
            MemoryProjectionDigest.VersionedDigest projectionDigest,
            String builtAt) {
        Objects.requireNonNull(projection, "projection must not be null");
        Objects.requireNonNull(projectionDigest, "projectionDigest must not be null");
        requireNonBlank(builtAt, "builtAt");

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", string("MEMORY_SEARCH#PROJECTION#" + projection.memoryId().value()));
        item.put("sk", string("META"));
        item.put("itemType", string("MEMORY_SEARCH_PROJECTION"));
        item.put("schemaVersion", number(SCHEMA_VERSION));
        item.put("memoryId", string(projection.memoryId().value()));
        item.put("activeSemanticVersion", number(projection.semanticVersion()));
        item.put("analyzerVersion", string(projection.analyzerVersion()));
        item.put("tokenKeyVersion", number(projection.tokenKeyVersion()));
        item.put("projectionDigestKeyVersion", number(projectionDigest.projectionDigestKeyVersion()));
        item.put("currentTermCount", number(projection.termCount()));
        item.put("currentTermPageCount", number(projection.pages().size()));
        item.put("fallbackRequired", bool(projection.fallbackRequired()));
        item.put("projectionDigest", string(projectionDigest.value()));
        item.put("status", string("READY"));
        item.put("builtAt", string(builtAt));
        return Map.copyOf(item);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }
}
