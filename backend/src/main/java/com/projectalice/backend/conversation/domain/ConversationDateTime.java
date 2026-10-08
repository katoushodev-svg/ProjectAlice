package com.projectalice.backend.conversation.domain;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class ConversationDateTime {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");
    private ConversationDateTime() { }
    public static String format(OffsetDateTime value) { return FORMAT.format(value); }
    public static OffsetDateTime parse(String value) { return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME); }
}
