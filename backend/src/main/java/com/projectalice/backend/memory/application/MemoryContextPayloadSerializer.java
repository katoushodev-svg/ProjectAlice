package com.projectalice.backend.memory.application;

import java.util.List;
import java.util.Objects;

/** Serializes ordered memory content as the provider-independent Memory Context payload. */
public final class MemoryContextPayloadSerializer {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Returns an empty string for no memories, otherwise a compact JSON array containing only
     * each item's content in the supplied order.
     */
    public String serialize(List<MemoryContextItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder payload = new StringBuilder();
        payload.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                payload.append(',');
            }
            MemoryContextItem item = Objects.requireNonNull(items.get(i), "items must not contain null");
            appendJsonString(payload, item.content());
        }
        payload.append(']');
        return payload.toString();
    }

    private static void appendJsonString(StringBuilder target, String value) {
        target.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    target.append(current).append(value.charAt(++i));
                } else {
                    appendUnicodeEscape(target, current);
                }
                continue;
            }
            if (Character.isLowSurrogate(current)) {
                appendUnicodeEscape(target, current);
                continue;
            }
            switch (current) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\t' -> target.append("\\t");
                case '\n' -> target.append("\\n");
                case '\f' -> target.append("\\f");
                case '\r' -> target.append("\\r");
                default -> {
                    if (current < 0x20) {
                        appendUnicodeEscape(target, current);
                    } else {
                        target.append(current);
                    }
                }
            }
        }
        target.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder target, char value) {
        target.append("\\u")
                .append(HEX[(value >>> 12) & 0x0f])
                .append(HEX[(value >>> 8) & 0x0f])
                .append(HEX[(value >>> 4) & 0x0f])
                .append(HEX[value & 0x0f]);
    }
}
