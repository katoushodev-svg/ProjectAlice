package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.PersonalMemory;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

final class DynamoDbPersonalMemoryMapper {

    private DynamoDbPersonalMemoryMapper() {
    }

    static Map<String, AttributeValue> toMemoryItem(PersonalMemory memory) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", string("MEMORY#" + memory.memoryId().value()));
        item.put("sk", string("META"));
        item.put("itemType", string("PERSONAL_MEMORY"));
        item.put("schemaVersion", number(1));
        item.put("memoryId", string(memory.memoryId().value()));
        item.put("content", string(memory.content().value()));
        item.put("category", string(memory.category().name()));
        item.put("captureType", string(memory.captureType().name()));
        item.put("sensitivityLevel", string(memory.sensitivityLevel().name()));
        item.put("memoryState", string(memory.state().name()));
        item.put("version", number(memory.version()));
        item.put("createdAt", string(memory.createdAt().toString()));
        item.put("updatedAt", string(memory.updatedAt().toString()));
        if (memory.confirmedAt() != null) {
            item.put("confirmedAt", string(memory.confirmedAt().toString()));
        }
        return item;
    }

    static PersonalMemory toDomain(Map<String, AttributeValue> item) {
        Objects.requireNonNull(item, "item must not be null");

        MemoryId memoryId = new MemoryId(requiredString(item, "memoryId"));
        MemoryContent content = new MemoryContent(requiredString(item, "content"));
        MemoryCategory category = MemoryCategory.valueOf(requiredString(item, "category"));
        CaptureType captureType = CaptureType.valueOf(requiredString(item, "captureType"));
        SensitivityLevel sensitivityLevel = SensitivityLevel.valueOf(
                requiredString(item, "sensitivityLevel"));
        MemoryState state = MemoryState.valueOf(requiredString(item, "memoryState"));
        long version = requiredLong(item, "version");
        OffsetDateTime createdAt = OffsetDateTime.parse(requiredString(item, "createdAt"));
        OffsetDateTime updatedAt = OffsetDateTime.parse(requiredString(item, "updatedAt"));

        AttributeValue confirmedAtAttribute = item.get("confirmedAt");
        OffsetDateTime confirmedAt = null;
        if (confirmedAtAttribute != null) {
            String confirmedAtValue = confirmedAtAttribute.s();
            if (confirmedAtValue == null) {
                throw new IllegalArgumentException("Invalid confirmedAt attribute: expected a string");
            }
            confirmedAt = OffsetDateTime.parse(confirmedAtValue);
        }

        return PersonalMemory.reconstitute(
                memoryId,
                content,
                category,
                captureType,
                sensitivityLevel,
                state,
                version,
                createdAt,
                updatedAt,
                confirmedAt);
    }

    static Map<String, AttributeValue> toCreateRevisionItem(PersonalMemory memory) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", string("MEMORY#" + memory.memoryId().value()));
        item.put("sk", string("REVISION#%020d".formatted(memory.version())));
        item.put("itemType", string("MEMORY_REVISION"));
        item.put("schemaVersion", number(1));
        item.put("memoryId", string(memory.memoryId().value()));
        item.put("revisionNumber", number(memory.version()));
        item.put("afterVersion", number(memory.version()));
        item.put("changeType", string("CREATE"));
        item.put("changedFields", list("CONTENT", "CATEGORY", "SENSITIVITY_LEVEL", "MEMORY_STATE"));
        item.put("afterSnapshot", AttributeValue.builder().m(snapshot(memory)).build());
        item.put("changedAt", string(memory.createdAt().toString()));
        item.put("changeSource", string("MANAGEMENT_API"));
        item.put("sourceReferences", list());
        item.put("reasonCode", string("INITIAL_SAVE"));
        return item;
    }

    static Map<String, AttributeValue> toUpdateRevisionItem(
            PersonalMemory before,
            PersonalMemory after,
            long expectedVersion,
            long nextVersion,
            List<String> changedFields,
            String reasonCode) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", string("MEMORY#" + after.memoryId().value()));
        item.put("sk", string("REVISION#%020d".formatted(nextVersion)));
        item.put("itemType", string("MEMORY_REVISION"));
        item.put("schemaVersion", number(1));
        item.put("memoryId", string(after.memoryId().value()));
        item.put("revisionNumber", number(nextVersion));
        item.put("beforeVersion", number(expectedVersion));
        item.put("afterVersion", number(nextVersion));
        item.put("changeType", string("UPDATE"));
        item.put("changedFields", list(changedFields.toArray(new String[0])));
        item.put("beforeSnapshot", AttributeValue.builder().m(snapshot(before)).build());
        item.put("afterSnapshot", AttributeValue.builder().m(snapshot(after)).build());
        item.put("changedAt", string(after.updatedAt().toString()));
        item.put("changeSource", string("MANAGEMENT_API"));
        item.put("sourceReferences", list());
        item.put("reasonCode", string(reasonCode));
        return item;
    }

    private static Map<String, AttributeValue> snapshot(PersonalMemory memory) {
        Map<String, AttributeValue> snapshot = new HashMap<>();
        snapshot.put("content", string(memory.content().value()));
        snapshot.put("category", string(memory.category().name()));
        snapshot.put("captureType", string(memory.captureType().name()));
        snapshot.put("sensitivityLevel", string(memory.sensitivityLevel().name()));
        snapshot.put("memoryState", string(memory.state().name()));
        snapshot.put("updatedAt", string(memory.updatedAt().toString()));
        if (memory.confirmedAt() != null) {
            snapshot.put("confirmedAt", string(memory.confirmedAt().toString()));
        }
        return snapshot;
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static String requiredString(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue attribute = item.get(attributeName);
        if (attribute == null || attribute.s() == null) {
            throw new IllegalArgumentException(
                    "Missing or invalid required string attribute: " + attributeName);
        }
        return attribute.s();
    }

    private static long requiredLong(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue attribute = item.get(attributeName);
        if (attribute == null || attribute.n() == null) {
            throw new IllegalArgumentException(
                    "Missing or invalid required numeric attribute: " + attributeName);
        }
        try {
            return Long.parseLong(attribute.n());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid required numeric attribute: " + attributeName, exception);
        }
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static AttributeValue list(String... values) {
        return AttributeValue.builder()
                .l(Arrays.stream(values).map(DynamoDbPersonalMemoryMapper::string).toList())
                .build();
    }
}
