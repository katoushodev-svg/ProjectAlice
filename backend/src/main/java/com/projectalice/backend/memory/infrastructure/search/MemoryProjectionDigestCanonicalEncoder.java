package com.projectalice.backend.memory.infrastructure.search;

import com.projectalice.backend.memory.domain.MemoryId;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Encodes the ADR-019 Canonical Byte Format v1 for a complete KW_V1 projection. */
public final class MemoryProjectionDigestCanonicalEncoder {

    private static final byte[] HEADER = {'A', 'P', 'D', 'G', 0, 1};
    private static final int MAX_PAGES = 6;
    private static final int MAX_DESCRIPTORS_PER_PAGE = 100;
    private static final int MAX_DESCRIPTORS = 512;

    public byte[] encode(Input input) {
        Objects.requireNonNull(input, "input must not be null");
        requirePositive(input.semanticVersion(), "semanticVersion");
        requirePositive(input.tokenKeyVersion(), "tokenKeyVersion");
        byte[] analyzerVersion = strictUtf8(input.analyzerVersion(), "analyzerVersion");
        if (analyzerVersion.length == 0) {
            throw new IllegalArgumentException("analyzerVersion must not be empty");
        }

        List<Page> pages = input.pages();
        if (pages.size() > MAX_PAGES || input.expectedPageCount() != pages.size()) {
            throw new IllegalArgumentException("page count does not match projection bounds");
        }
        if (input.expectedTermCount() < 0 || input.expectedTermCount() > MAX_DESCRIPTORS) {
            throw new IllegalArgumentException("term count is outside projection bounds");
        }

        int descriptorTotal = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(HEADER);
        writeStringField(output, 0x01, input.memoryId().value());
        writeUnsignedField(output, 0x02, input.semanticVersion(), 8);
        writeField(output, 0x03, analyzerVersion);
        writeUnsignedField(output, 0x04, input.tokenKeyVersion(), 4);
        writeUnsignedField(output, 0x05, pages.size(), 4);

        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            if (page.pageNumber() != index) {
                throw new IllegalArgumentException("page numbers must be contiguous and ascending from zero");
            }
            int count = page.descriptors().size();
            if (count < 1 || count > MAX_DESCRIPTORS_PER_PAGE) {
                throw new IllegalArgumentException("each page must contain between 1 and 100 descriptors");
            }
            descriptorTotal = Math.addExact(descriptorTotal, count);
            if (descriptorTotal > MAX_DESCRIPTORS) {
                throw new IllegalArgumentException("projection exceeds the descriptor limit");
            }

            ByteArrayOutputStream pageValue = new ByteArrayOutputStream();
            writeUnsignedField(pageValue, 0x11, page.pageNumber(), 4);
            writeUnsignedField(pageValue, 0x12, count, 4);
            for (Descriptor descriptor : page.descriptors()) {
                ByteArrayOutputStream descriptorValue = new ByteArrayOutputStream();
                writeStringField(descriptorValue, 0x21, requireTermDigest(descriptor.termDigest()));
                writeUnsignedField(descriptorValue, 0x22, termKindCode(descriptor.termKind()), 1);
                int expectedWeightCode = descriptor.termKind().weightCode();
                if (descriptor.weightCode() != expectedWeightCode) {
                    throw new IllegalArgumentException("weightCode does not match termKind");
                }
                requirePositive(descriptor.weightCode(), "weightCode");
                writeUnsignedField(descriptorValue, 0x23, descriptor.weightCode(), 4);
                writeField(pageValue, 0x20, pageValue(descriptorValue));
            }
            writeField(output, 0x10, pageValue(pageValue));
        }

        if (descriptorTotal != input.expectedTermCount()) {
            throw new IllegalArgumentException("descriptor count does not match expected term count");
        }
        return output.toByteArray();
    }

    /** Parses persisted canonical bytes, rejecting any non-canonical or malformed representation. */
    public Input decode(byte[] canonicalBytes) {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes must not be null");
        Reader reader = new Reader(canonicalBytes);
        if (!Arrays.equals(reader.readBytes(HEADER.length), HEADER)) {
            throw new IllegalArgumentException("invalid projection digest header or format version");
        }

        String memoryId = readString(reader.readField(0x01));
        long semanticVersion = readUnsigned(reader.readField(0x02), 8);
        String analyzerVersion = readString(reader.readField(0x03));
        int tokenKeyVersion = toInt(readUnsigned(reader.readField(0x04), 4), "tokenKeyVersion");
        int pageCount = toInt(readUnsigned(reader.readField(0x05), 4), "pageCount");
        if (pageCount > MAX_PAGES) {
            throw new IllegalArgumentException("page count exceeds projection bounds");
        }

        java.util.ArrayList<Page> pages = new java.util.ArrayList<>(pageCount);
        int descriptorTotal = 0;
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            Reader pageReader = new Reader(reader.readField(0x10));
            int pageNumber = toInt(readUnsigned(pageReader.readField(0x11), 4), "pageNumber");
            int descriptorCount = toInt(readUnsigned(pageReader.readField(0x12), 4), "descriptorCount");
            if (descriptorCount < 1 || descriptorCount > MAX_DESCRIPTORS_PER_PAGE) {
                throw new IllegalArgumentException("invalid descriptor count");
            }
            java.util.ArrayList<Descriptor> descriptors = new java.util.ArrayList<>(descriptorCount);
            for (int descriptorIndex = 0; descriptorIndex < descriptorCount; descriptorIndex++) {
                Reader descriptorReader = new Reader(pageReader.readField(0x20));
                String termDigest = readString(descriptorReader.readField(0x21));
                int termKindCode = toInt(readUnsigned(descriptorReader.readField(0x22), 1), "termKind");
                int weightCode = toInt(readUnsigned(descriptorReader.readField(0x23), 4), "weightCode");
                descriptorReader.requireEnd();
                descriptors.add(new Descriptor(termDigest, termKind(termKindCode), weightCode));
            }
            pageReader.requireEnd();
            descriptorTotal = Math.addExact(descriptorTotal, descriptorCount);
            if (descriptorTotal > MAX_DESCRIPTORS) {
                throw new IllegalArgumentException("projection exceeds the descriptor limit");
            }
            pages.add(new Page(pageNumber, descriptors));
        }
        reader.requireEnd();

        Input input = new Input(
                new MemoryId(memoryId), semanticVersion, analyzerVersion, tokenKeyVersion,
                pageCount, descriptorTotal, pages);
        if (!Arrays.equals(encode(input), canonicalBytes)) {
            throw new IllegalArgumentException("projection bytes are not canonical");
        }
        return input;
    }

    private static int termKindCode(KwV1Analyzer.TermKind termKind) {
        Objects.requireNonNull(termKind, "termKind must not be null");
        return switch (termKind) {
            case LEXICAL -> 1;
            case NGRAM_3 -> 2;
            case NGRAM_2 -> 3;
            case NGRAM_1 -> 4;
        };
    }

    private static KwV1Analyzer.TermKind termKind(int code) {
        return switch (code) {
            case 1 -> KwV1Analyzer.TermKind.LEXICAL;
            case 2 -> KwV1Analyzer.TermKind.NGRAM_3;
            case 3 -> KwV1Analyzer.TermKind.NGRAM_2;
            case 4 -> KwV1Analyzer.TermKind.NGRAM_1;
            default -> throw new IllegalArgumentException("unknown canonical termKind code");
        };
    }

    private static long readUnsigned(byte[] value, int width) {
        if (value.length != width) {
            throw new IllegalArgumentException("fixed-width field has an invalid length");
        }
        if (width == 8) {
            long result = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).getLong();
            if (result < 0) {
                throw new IllegalArgumentException("unsigned value exceeds the supported Java range");
            }
            return result;
        }
        long result = 0;
        for (byte current : value) {
            result = (result << 8) | (current & 0xffL);
        }
        return result;
    }

    private static int toInt(long value, String name) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds the supported Java range");
        }
        return (int) value;
    }

    private static String readString(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("string field is not valid UTF-8", exception);
        }
    }

    private static String requireTermDigest(String value) {
        Objects.requireNonNull(value, "termDigest must not be null");
        if (!value.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("termDigest must be an unpadded Base64url SHA-256 value");
        }
        byte[] decoded;
        try {
            decoded = java.util.Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("termDigest must be an unpadded Base64url SHA-256 value", exception);
        }
        if (decoded.length != 32
                || !java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("termDigest must be a canonical 32-byte Base64url digest");
        }
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static byte[] strictUtf8(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(name + " is not valid Unicode", exception);
        }
    }

    private static void writeStringField(ByteArrayOutputStream output, int tag, String value) {
        writeField(output, tag, strictUtf8(value, "string field"));
    }

    private static void writeUnsignedField(ByteArrayOutputStream output, int tag, long value, int width) {
        if (value < 0 || (width < 8 && value >= (1L << (width * 8)))) {
            throw new IllegalArgumentException("unsigned integer does not fit its field width");
        }
        byte[] bytes = new byte[width];
        for (int index = width - 1; index >= 0; index--) {
            bytes[index] = (byte) (value & 0xff);
            value >>>= 8;
        }
        writeField(output, tag, bytes);
    }

    private static void writeField(ByteArrayOutputStream output, int tag, byte[] value) {
        if (tag < 0 || tag > 0xff) {
            throw new IllegalArgumentException("invalid field");
        }
        output.write(tag);
        output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.length).array());
        output.writeBytes(value);
    }

    private static byte[] pageValue(ByteArrayOutputStream value) {
        return value.toByteArray();
    }

    private static final class Reader {
        private final byte[] bytes;
        private int position;

        private Reader(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "bytes must not be null");
        }

        private byte[] readField(int expectedTag) {
            if (remaining() < 5) {
                throw new IllegalArgumentException("truncated field header");
            }
            int tag = readBytes(1)[0] & 0xff;
            if (tag != expectedTag) {
                throw new IllegalArgumentException("unexpected field tag or order");
            }
            int length = ByteBuffer.wrap(readBytes(4)).order(ByteOrder.BIG_ENDIAN).getInt();
            if (length < 0 || length > remaining()) {
                throw new IllegalArgumentException("invalid field length");
            }
            return readBytes(length);
        }

        private byte[] readBytes(int length) {
            if (length < 0 || length > remaining()) {
                throw new IllegalArgumentException("truncated projection digest bytes");
            }
            byte[] result = Arrays.copyOfRange(bytes, position, position + length);
            position += length;
            return result;
        }

        private int remaining() {
            return bytes.length - position;
        }

        private void requireEnd() {
            if (remaining() != 0) {
                throw new IllegalArgumentException("unexpected trailing projection digest bytes");
            }
        }
    }

    public record Input(
            MemoryId memoryId,
            long semanticVersion,
            String analyzerVersion,
            int tokenKeyVersion,
            int expectedPageCount,
            int expectedTermCount,
            List<Page> pages) {
        public Input {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            Objects.requireNonNull(analyzerVersion, "analyzerVersion must not be null");
            pages = List.copyOf(Objects.requireNonNull(pages, "pages must not be null"));
        }
    }

    public record Page(int pageNumber, List<Descriptor> descriptors) {
        public Page {
            descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors must not be null"));
        }
    }

    public record Descriptor(String termDigest, KwV1Analyzer.TermKind termKind, int weightCode) {}
}
