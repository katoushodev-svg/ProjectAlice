package com.projectalice.backend.conversation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectalice.backend.conversation.application.model.InvalidCursorException;
import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.domain.ConversationDateTime;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ConversationReadService {
    private static final int RESPONSE_LIMIT = 16 * 1024 * 1024;
    private final ConversationRepository repository;
    private final ObjectMapper mapper;
    private final byte[] signingKey;

    public ConversationReadService(ConversationRepository repository, byte[] signingKey) {
        if (signingKey.length < 32) throw new IllegalArgumentException("Cursor signing key must contain at least 32 bytes");
        this.repository = repository; this.mapper = new ObjectMapper(); this.signingKey = signingKey.clone();
    }

    public ConversationRepository.ConversationState getConversation() {
        return repository.findConversation().orElseThrow(() -> new ConversationSendException(
                "CONVERSATION_NOT_FOUND", 404, "Conversationはまだ作成されていません。"));
    }

    public MessagePage getMessages(Integer requestedLimit, String cursor) {
        int limit = requestedLimit == null ? 50 : requestedLimit;
        if (limit < 1 || limit > 100) throw new ConversationSendException("VALIDATION_ERROR",400,"limitは1から100で指定してください。");
        Cursor cursorValue = cursor == null ? null : decode(cursor);
        var conversation = repository.findConversation();
        if (conversation.isEmpty()) {
            if (cursor != null) throw new InvalidCursorException();
            return new MessagePage(List.of(), null, false);
        }
        if (cursorValue != null && !conversation.get().id().equals(cursorValue.conversationId())) throw new InvalidCursorException();
        Long before = cursorValue == null ? null : cursorValue.sequence();
        List<ConversationMessage> candidates = before == null ? repository.findLatestMessages(limit + 1)
                : repository.findMessagesBefore(before, limit + 1);
        List<ConversationMessage> ascending = new ArrayList<>(candidates);
        ascending.sort(java.util.Comparator.comparingLong(ConversationMessage::sequence));
        boolean more = ascending.size() > limit;
        if (more) ascending = new ArrayList<>(ascending.subList(ascending.size() - limit, ascending.size()));
        while (!ascending.isEmpty() && jsonBytes(ascending,
                more ? encode(conversation.get().id(), ascending.get(0).sequence()) : null, more) > RESPONSE_LIMIT) {
            ascending.remove(0);
            more = true;
        }
        if (ascending.isEmpty() && !candidates.isEmpty()) throw new IllegalStateException("A single message exceeds the API response size limit");
        String next = more ? encode(conversation.get().id(), ascending.get(0).sequence()) : null;
        return new MessagePage(List.copyOf(ascending), next, more);
    }

    private int jsonBytes(List<ConversationMessage> messages, String cursor, boolean hasMore) {
        try { return mapper.writeValueAsBytes(toMap(messages,cursor,hasMore)).length; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public Map<String,Object> toMap(List<ConversationMessage> messages, String cursor, boolean hasMore) {
        List<Map<String,Object>> rows = messages.stream().map(m -> {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("id",m.messageId()); row.put("role",m.role().name().toLowerCase());
            row.put("content",m.content()); row.put("createdAt",ConversationDateTime.format(m.createdAt())); return row;
        }).toList();
        Map<String,Object> result = new LinkedHashMap<>(); result.put("messages",rows); result.put("nextCursor",cursor); result.put("hasMore",hasMore); return result;
    }
    private String encode(String conversationId,long sequence) {
        try {
            byte[] payload = mapper.writeValueAsBytes(Map.of("v",1,"conversationId",conversationId,"exclusiveStartSequence",sequence));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)+"."+
                    Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Cursor decode(String cursor) {
        try {
            if (cursor.length()>1024 || !cursor.matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")) throw new Exception();
            String[] parts=cursor.split("\\.",-1); byte[] payload=Base64.getUrlDecoder().decode(parts[0]); byte[] signature=Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(hmac(payload),signature)) throw new Exception();
            var node=mapper.readTree(payload);
            if (!node.isObject() || node.size()!=3 || !node.has("v") || !node.has("conversationId") || !node.has("exclusiveStartSequence") ||
                    !node.get("v").isIntegralNumber() || !node.get("v").canConvertToInt() || node.get("v").intValue()!=1 || !node.get("conversationId").isTextual() ||
                    !node.get("exclusiveStartSequence").isIntegralNumber() || !node.get("exclusiveStartSequence").canConvertToLong() ||
                    node.get("exclusiveStartSequence").longValue()<1) throw new Exception();
            return new Cursor(node.get("conversationId").textValue(),node.get("exclusiveStartSequence").longValue());
        } catch (Exception invalid) { throw new InvalidCursorException(); }
    }
    private record Cursor(String conversationId,long sequence) { }
    private byte[] hmac(byte[] bytes) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey,"HmacSHA256")); return mac.doFinal(bytes); }
    public record MessagePage(List<ConversationMessage> messages,String nextCursor,boolean hasMore) { }
}
