package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.projectalice.backend.memory.domain.MemoryId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryProjectionDigestCanonicalEncoderTest {

    private static final MemoryId MEMORY_ID = new MemoryId("00000000-0000-4000-8000-000000000001");
    private final MemoryProjectionDigestCanonicalEncoder encoder = new MemoryProjectionDigestCanonicalEncoder();

    @Test
    void encodesEmptyProjectionToFixedCanonicalBytesAndDecodesIt() {
        var input = new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 0, 0, List.of());
        byte[] expected = HexFormat.of().parseHex(
                "415044470001"
                        + "010000002430303030303030302d303030302d343030302d383030302d303030303030303030303031"
                        + "02000000080000000000000001"
                        + "03000000054b575f5631"
                        + "040000000400000001"
                        + "050000000400000000");

        byte[] actual = encoder.encode(input);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, encoder.encode(encoder.decode(expected)));
        assertEquals(0, encoder.decode(expected).expectedTermCount());
    }

    @Test
    void encodesOrderedPagesAndDescriptorsToFixedCanonicalBytes() {
        var input = new MemoryProjectionDigestCanonicalEncoder.Input(
                MEMORY_ID,
                7,
                "KW_V1",
                2,
                2,
                2,
                List.of(
                        new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(
                                new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                        "A".repeat(43), KwV1Analyzer.TermKind.LEXICAL, 4))),
                        new MemoryProjectionDigestCanonicalEncoder.Page(1, List.of(
                                new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                        "_".repeat(42) + "8", KwV1Analyzer.TermKind.NGRAM_2, 2)))));
        byte[] expected = HexFormat.of().parseHex(
                "415044470001010000002430303030303030302d303030302d343030302d383030302d3030303030"
                        + "303030303030310200000008000000000000000703000000054b575f563104000000040000000205"
                        + "00000004000000021000000056110000000400000000120000000400000001200000003f21000000"
                        + "2b414141414141414141414141414141414141414141414141414141414141414141414141414141"
                        + "41414141220000000101230000000400000004100000005611000000040000000112000000040000"
                        + "0001200000003f210000002b5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f5f"
                        + "5f5f5f5f5f5f5f5f5f5f5f5f5f5f38220000000103230000000400000002");

        byte[] actual = encoder.encode(input);

        assertArrayEquals(expected, actual);
        assertArrayEquals(actual, encoder.encode(input));
        assertEquals(input, encoder.decode(actual));
    }

    @Test
    void rejectsMalformedHeaderVersionTagLengthOrderUtf8AndTrailingBytes() {
        byte[] canonical = encoder.encode(emptyInput());

        assertInvalidAfterMutation(canonical, 0, (byte) 'X');
        assertInvalidAfterMutation(canonical, 5, (byte) 2);
        assertInvalidAfterMutation(canonical, 6, (byte) 0x7f);
        assertInvalidAfterMutation(canonical, 10, (byte) 0x25); // memoryId field length no longer matches
        assertInvalidAfterMutation(canonical, 47, (byte) 0x03); // field order/tag
        assertInvalidAfterMutation(canonical, 65, (byte) 0xff); // malformed analyzerVersion UTF-8
        byte[] withTrailingByte = java.util.Arrays.copyOf(canonical, canonical.length + 1);
        assertThrows(IllegalArgumentException.class, () -> encoder.decode(withTrailingByte));
    }

    @Test
    void rejectsInvalidVersionsPageNumbersCountsAndDescriptors() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 0, "KW_V1", 1, 0, 0, List.of())));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 0, 0, 0, List.of())));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "", 1, 0, 0, List.of())));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1\uD800", 1, 0, 0, List.of())));

        var descriptor = new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                "A".repeat(43), KwV1Analyzer.TermKind.LEXICAL, 4);
        assertInvalidPages(List.of(new MemoryProjectionDigestCanonicalEncoder.Page(1, List.of(descriptor))), 1);
        assertInvalidPages(List.of(
                new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(descriptor)),
                new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(descriptor))), 2);
        assertInvalidPages(List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of())), 0);
        assertInvalidPages(List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0,
                java.util.Collections.nCopies(101, descriptor))), 101);
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 1, 2,
                        List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(descriptor))))));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 1, 1,
                        List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(
                                new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                        "not-a-digest", KwV1Analyzer.TermKind.LEXICAL, 4)))))));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 1, 1,
                        List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(
                                new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                                        "A".repeat(43), KwV1Analyzer.TermKind.LEXICAL, 1)))))));

        byte[] valid = encoder.encode(new MemoryProjectionDigestCanonicalEncoder.Input(
                MEMORY_ID, 1, "KW_V1", 1, 1, 1,
                List.of(new MemoryProjectionDigestCanonicalEncoder.Page(0, List.of(descriptor)))));
        int kindTagOffset = indexOf(valid, new byte[] {0x22, 0, 0, 0, 1, 1});
        valid[kindTagOffset + 5] = 9;
        byte[] malformedKind = valid;
        assertThrows(IllegalArgumentException.class, () -> encoder.decode(malformedKind));
    }

    @Test
    void enforcesMaximumPageCountAndContiguousPageSequence() {
        var descriptor = new MemoryProjectionDigestCanonicalEncoder.Descriptor(
                "A".repeat(43), KwV1Analyzer.TermKind.LEXICAL, 4);
        List<MemoryProjectionDigestCanonicalEncoder.Page> sevenPages = new ArrayList<>();
        for (int page = 0; page < 7; page++) {
            sevenPages.add(new MemoryProjectionDigestCanonicalEncoder.Page(page, List.of(descriptor)));
        }
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 7, 7, sevenPages)));
    }

    private void assertInvalidAfterMutation(byte[] source, int offset, byte value) {
        byte[] mutated = source.clone();
        mutated[offset] = value;
        assertThrows(IllegalArgumentException.class, () -> encoder.decode(mutated));
    }

    private void assertInvalidPages(List<MemoryProjectionDigestCanonicalEncoder.Page> pages, int count) {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(
                new MemoryProjectionDigestCanonicalEncoder.Input(
                        MEMORY_ID, 1, "KW_V1", 1, pages.size(), count, pages)));
    }

    private MemoryProjectionDigestCanonicalEncoder.Input emptyInput() {
        return new MemoryProjectionDigestCanonicalEncoder.Input(MEMORY_ID, 1, "KW_V1", 1, 0, 0, List.of());
    }

    private static int indexOf(byte[] source, byte[] target) {
        outer:
        for (int index = 0; index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        throw new AssertionError("byte sequence not found");
    }
}
