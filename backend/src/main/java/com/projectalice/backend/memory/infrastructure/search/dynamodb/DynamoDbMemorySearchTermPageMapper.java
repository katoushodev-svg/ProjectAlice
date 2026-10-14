package com.projectalice.backend.memory.infrastructure.search.dynamodb;

import com.projectalice.backend.memory.infrastructure.search.KwV1MemoryProjectionBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.projectalice.backend.memory.infrastructure.search.KwV1Analyzer;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Maps an ordered projection page to its content-free DynamoDB item. */
public final class DynamoDbMemorySearchTermPageMapper {

    private static final int MAX_PAGE_NUMBER = 9_999;

    private DynamoDbMemorySearchTermPageMapper() {}

    /**
     * Maps the page at the supplied zero-based position in the projection. The position is also
     * persisted in {@code pageNumber} and used in the page sort key.
     */
    public static Map<String, AttributeValue> map(
            KwV1MemoryProjectionBuilder.Projection projection, int pageNumber) {
        Objects.requireNonNull(projection, "projection must not be null");
        if (pageNumber < 0 || pageNumber > MAX_PAGE_NUMBER || pageNumber >= projection.pages().size()) {
            throw new IllegalArgumentException("pageNumber must identify a page in the projection");
        }

        KwV1MemoryProjectionBuilder.TermPage page = projection.pages().get(pageNumber);
        if (page.terms().isEmpty() || page.terms().size() > KwV1MemoryProjectionBuilder.TERMS_PER_PAGE) {
            throw new IllegalArgumentException("term page must contain between 1 and 100 descriptors");
        }

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", string("MEMORY_SEARCH#PROJECTION#" + projection.memoryId().value()));
        item.put("sk", string("VERSION#%020d#TERMS#%04d".formatted(
                projection.semanticVersion(), pageNumber)));
        item.put("semanticVersion", number(projection.semanticVersion()));
        item.put("analyzerVersion", string(projection.analyzerVersion()));
        item.put("tokenKeyVersion", number(projection.tokenKeyVersion()));
        item.put("pageNumber", number(pageNumber));
        item.put("descriptors", descriptorList(page));
        return Map.copyOf(item);
    }

    /** Reconstitutes one persisted page, rejecting malformed or mismatched DynamoDB attributes. */
    public static PersistedPage read(
            Map<String, AttributeValue> item,
            com.projectalice.backend.memory.domain.MemoryId expectedMemoryId,
            long expectedSemanticVersion,
            String expectedAnalyzerVersion,
            int expectedTokenKeyVersion,
            int expectedPageNumber) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(expectedMemoryId, "expectedMemoryId must not be null");
        Objects.requireNonNull(expectedAnalyzerVersion, "expectedAnalyzerVersion must not be null");
        String expectedPk = "MEMORY_SEARCH#PROJECTION#" + expectedMemoryId.value();
        String expectedSk = "VERSION#%020d#TERMS#%04d".formatted(expectedSemanticVersion, expectedPageNumber);
        if (!expectedPk.equals(stringValue(item, "pk")) || !expectedSk.equals(stringValue(item, "sk"))
                || expectedSemanticVersion != numberValue(item, "semanticVersion")
                || !expectedAnalyzerVersion.equals(stringValue(item, "analyzerVersion"))
                || expectedTokenKeyVersion != numberValue(item, "tokenKeyVersion")
                || expectedPageNumber != numberValue(item, "pageNumber")) {
            throw new IllegalArgumentException("persisted term page attributes do not match expected version");
        }
        AttributeValue descriptorAttribute = item.get("descriptors");
        if (descriptorAttribute == null || !descriptorAttribute.hasL()
                || descriptorAttribute.l().isEmpty()
                || descriptorAttribute.l().size() > KwV1MemoryProjectionBuilder.TERMS_PER_PAGE) {
            throw new IllegalArgumentException("persisted term page has invalid descriptors");
        }
        List<KwV1MemoryProjectionBuilder.TermDescriptor> descriptors = new ArrayList<>();
        for (AttributeValue value : descriptorAttribute.l()) {
            if (!value.hasM()) throw new IllegalArgumentException("persisted descriptor must be a map");
            Map<String, AttributeValue> fields = value.m();
            if (!fields.keySet().equals(java.util.Set.of("termDigest", "termKind", "weightCode"))) {
                throw new IllegalArgumentException("persisted descriptor has unexpected fields");
            }
            try {
                descriptors.add(new KwV1MemoryProjectionBuilder.TermDescriptor(
                        stringValue(fields, "termDigest"),
                        KwV1Analyzer.TermKind.valueOf(stringValue(fields, "termKind")),
                        Math.toIntExact(numberValue(fields, "weightCode"))));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("persisted descriptor is invalid", exception);
            }
        }
        return new PersistedPage(expectedPageNumber, expectedSemanticVersion, expectedAnalyzerVersion,
                expectedTokenKeyVersion, descriptors);
    }

    public record PersistedPage(int pageNumber, long semanticVersion, String analyzerVersion,
            int tokenKeyVersion, List<KwV1MemoryProjectionBuilder.TermDescriptor> descriptors) {
        public PersistedPage { descriptors = List.copyOf(descriptors); }
    }

    private static String stringValue(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null) throw new IllegalArgumentException("missing string attribute: " + name);
        return value.s();
    }

    private static long numberValue(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null) throw new IllegalArgumentException("missing number attribute: " + name);
        try { return Long.parseLong(value.n()); }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid number attribute: " + name, exception);
        }
    }

    private static AttributeValue descriptorList(KwV1MemoryProjectionBuilder.TermPage page) {
        List<AttributeValue> descriptors = new ArrayList<>(page.terms().size());
        for (KwV1MemoryProjectionBuilder.TermDescriptor descriptor : page.terms()) {
            Map<String, AttributeValue> attributes = new HashMap<>();
            attributes.put("termDigest", string(descriptor.termDigest()));
            attributes.put("termKind", string(descriptor.termKind().name()));
            attributes.put("weightCode", number(descriptor.weightCode()));
            descriptors.add(AttributeValue.builder().m(Map.copyOf(attributes)).build());
        }
        return AttributeValue.builder().l(List.copyOf(descriptors)).build();
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
