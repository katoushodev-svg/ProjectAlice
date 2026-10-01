package com.projectalice.backend.memory.infrastructure.persistence.dynamodb;

import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static AttributeValue list(String... values) {
        return AttributeValue.builder()
                .l(Arrays.stream(values).map(DynamoDbPersonalMemoryMapper::string).toList())
                .build();
    }
}
