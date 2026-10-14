package com.projectalice.backend.conversation.infrastructure.persistence.dynamodb;

import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.application.model.ConversationPersistenceConflictException;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import com.projectalice.backend.conversation.domain.ConversationDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

/** DynamoDB mapping for the Phase 1 single-conversation table model. */
public final class DynamoDbConversationRepository implements ConversationRepository {
    private static final String PK = "CONVERSATION#PRIMARY";
    private final DynamoDbClient client;
    private final String table;

    public DynamoDbConversationRepository(DynamoDbClient client, String table) {
        this.client = Objects.requireNonNull(client);
        this.table = Objects.requireNonNull(table);
        if (table.isBlank()) throw new IllegalArgumentException("table must not be blank");
    }

    @Override public List<ConversationMessage> findLatestMessages(int limit) {
        return queryMessages(null, limit);
    }

    @Override public List<ConversationMessage> findMessagesBefore(long exclusiveStartSequence, int limit) {
        return queryMessages(exclusiveStartSequence, limit);
    }

    private List<ConversationMessage> queryMessages(Long exclusiveStartSequence, int limit) {
        var response = client.query(QueryRequest.builder().tableName(table).consistentRead(true)
                .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                .expressionAttributeValues(Map.of(":pk", s(PK), ":prefix", s("MESSAGE#")))
                .scanIndexForward(false).limit(limit)
                .exclusiveStartKey(exclusiveStartSequence == null ? null : key(messageSk(exclusiveStartSequence))).build());
        List<ConversationMessage> messages = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) messages.add(message(item));
        var lastKey = response.lastEvaluatedKey();
        while (messages.size() < limit && lastKey != null && !lastKey.isEmpty()) {
            response = client.query(QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(":pk",s(PK),":prefix",s("MESSAGE#")))
                    .scanIndexForward(false).limit(limit-messages.size()).exclusiveStartKey(lastKey).build());
            for (Map<String,AttributeValue> item : response.items()) messages.add(message(item));
            lastKey=response.lastEvaluatedKey();
        }
        return messages;
    }

    @Override public Optional<ConversationState> findConversation() {
        Map<String, AttributeValue> item = get("METADATA");
        if (item.isEmpty()) return Optional.empty();
        return Optional.of(new ConversationState(str(item,"conversationId"), ConversationDateTime.parse(str(item,"createdAt")),
                ConversationDateTime.parse(str(item,"updatedAt")), num(item,"lastMessageSequence"),
                nullable(item,"activeIdempotencyKey"), numOr(item,"busyLeaseExpiresAtEpochSeconds",0)));
    }

    @Override public Optional<IdempotencyState> findIdempotency(String key) {
        Map<String, AttributeValue> item = get("IDEMPOTENCY#" + key);
        if (item.isEmpty()) return Optional.empty();
        long userSeq = num(item,"userMessageSequence");
        ConversationMessage user = getMessage(userSeq).orElseThrow(() -> new IllegalStateException("Canonical user message missing"));
        String state = str(item,"state");
        ConversationMessage assistant = "COMPLETED".equals(state)
                ? getMessage(num(item,"assistantMessageSequence")).orElseThrow(() -> new IllegalStateException("Canonical assistant message missing")) : null;
        return Optional.of(new IdempotencyState(key, str(item,"requestContentHash"), state, user, assistant,
                nullable(item,"failureCode"), numOr(item,"leaseExpiresAtEpochSeconds",0), numOr(item,"expiresAtEpochSeconds",0)));
    }

    @Override public void start(SendStart start) {
        Map<String, AttributeValue> user = messageItem(start.userMessage(), start.key());
        Map<String, AttributeValue> idem = new HashMap<>();
        idem.put("pk",s(PK)); idem.put("sk",s("IDEMPOTENCY#"+start.key())); idem.put("itemType",s("IDEMPOTENCY"));
        idem.put("idempotencyKey",s(start.key())); idem.put("requestContentHash",s(start.contentHash())); idem.put("state",s("PROCESSING"));
        idem.put("userMessageId",s(start.userMessage().messageId())); idem.put("userMessageSequence",n(start.userMessage().sequence()));
        idem.put("createdAt",s(ConversationDateTime.format(start.now()))); idem.put("updatedAt",s(ConversationDateTime.format(start.now())));
        idem.put("leaseExpiresAtEpochSeconds",n(start.leaseExpiresAtEpochSeconds()));
        var conv = findConversation().orElse(null);
        List<TransactWriteItem> writes = new ArrayList<>();
        if (conv == null) {
            Map<String,AttributeValue> c = new HashMap<>(); c.put("pk",s(PK)); c.put("sk",s("METADATA")); c.put("itemType",s("CONVERSATION"));
            c.put("conversationId",s(start.conversationId())); c.put("createdAt",s(ConversationDateTime.format(start.now()))); c.put("updatedAt",s(ConversationDateTime.format(start.now())));
            c.put("lastMessageSequence",n(start.userMessage().sequence())); c.put("activeIdempotencyKey",s(start.key()));
            c.put("busyLeaseExpiresAtEpochSeconds",n(start.leaseExpiresAtEpochSeconds()));
            writes.add(TransactWriteItem.builder().put(put(c,"attribute_not_exists(pk)")).build());
        } else {
            Update update = Update.builder().tableName(table).key(key("METADATA"))
                    .updateExpression("SET lastMessageSequence=:next, updatedAt=:at, activeIdempotencyKey=:key, busyLeaseExpiresAtEpochSeconds=:lease")
                    .conditionExpression("lastMessageSequence=:expected AND attribute_not_exists(activeIdempotencyKey)")
                    .expressionAttributeValues(Map.of(":next",n(start.userMessage().sequence()), ":expected",n(conv.lastSequence()),
                            ":at",s(ConversationDateTime.format(start.now())), ":key",s(start.key()), ":lease",n(start.leaseExpiresAtEpochSeconds()))).build();
            writes.add(TransactWriteItem.builder().update(update).build());
        }
        writes.add(TransactWriteItem.builder().put(put(user,"attribute_not_exists(pk)")).build());
        Put idemPut = Put.builder().tableName(table).item(idem)
                .conditionExpression("attribute_not_exists(pk) OR (#state=:failed AND expiresAtEpochSeconds<=:now)")
                .expressionAttributeNames(Map.of("#state","state"))
                .expressionAttributeValues(Map.of(":failed",s("FAILED"), ":now",n(start.now().toEpochSecond())))
                .build();
        writes.add(TransactWriteItem.builder().put(idemPut).build());
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(writes).build());
        } catch (TransactionCanceledException conflict) {
            throw new ConversationPersistenceConflictException();
        }
    }

    @Override public void complete(String key, ConversationMessage assistant) {
        var conv = findConversation().orElseThrow();
        Update conversation = Update.builder().tableName(table).key(key("METADATA"))
                .updateExpression("SET lastMessageSequence=:next, updatedAt=:at REMOVE activeIdempotencyKey, busyLeaseExpiresAtEpochSeconds")
                .conditionExpression("activeIdempotencyKey=:key AND lastMessageSequence=:expected")
                .expressionAttributeValues(Map.of(":next",n(assistant.sequence()), ":at",s(ConversationDateTime.format(assistant.createdAt())),
                        ":key",s(key), ":expected",n(conv.lastSequence()))).build();
        Update idem = Update.builder().tableName(table).key(key("IDEMPOTENCY#"+key))
                .updateExpression("SET #state=:completed, assistantMessageId=:id, assistantMessageSequence=:seq, completedAt=:at, updatedAt=:at REMOVE leaseExpiresAtEpochSeconds")
                .conditionExpression("#state=:processing")
                .expressionAttributeNames(Map.of("#state","state"))
                .expressionAttributeValues(Map.of(":completed",s("COMPLETED"), ":processing",s("PROCESSING"), ":id",s(assistant.messageId()),
                        ":seq",n(assistant.sequence()), ":at",s(ConversationDateTime.format(assistant.createdAt())))).build();
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().put(put(messageItem(assistant,key),"attribute_not_exists(pk)")).build(),
                TransactWriteItem.builder().update(conversation).build(), TransactWriteItem.builder().update(idem).build())).build());
    }

    @Override public void fail(String key, String code, OffsetDateTime at, long expires) {
        Update idem = Update.builder().tableName(table).key(key("IDEMPOTENCY#"+key))
                .updateExpression("SET #state=:failed, failureCode=:code, failedAt=:at, updatedAt=:at, expiresAtEpochSeconds=:expires REMOVE leaseExpiresAtEpochSeconds")
                .conditionExpression("#state=:processing").expressionAttributeNames(Map.of("#state","state"))
                .expressionAttributeValues(Map.of(":failed",s("FAILED"), ":processing",s("PROCESSING"), ":code",s(code),
                        ":at",s(ConversationDateTime.format(at)), ":expires",n(expires))).build();
        Update unlock = unlock(key);
        client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(List.of(
                TransactWriteItem.builder().update(idem).build(), TransactWriteItem.builder().update(unlock).build())).build());
    }

    @Override public void recoverExpired(String key, OffsetDateTime at, long expires) { fail(key,"REQUEST_INTERRUPTED",at,expires); }

    private Update unlock(String key) { return Update.builder().tableName(table).key(key("METADATA"))
            .updateExpression("REMOVE activeIdempotencyKey, busyLeaseExpiresAtEpochSeconds")
            .conditionExpression("activeIdempotencyKey=:key").expressionAttributeValues(Map.of(":key",s(key))).build(); }

    private Optional<ConversationMessage> getMessage(long seq) {
        var item = client.getItem(GetItemRequest.builder().tableName(table).key(key(messageSk(seq))).consistentRead(true).build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(message(item));
    }
    private Map<String,AttributeValue> get(String sk) { var item=client.getItem(GetItemRequest.builder().tableName(table).key(key(sk)).consistentRead(true).build()).item(); return item == null ? Map.of() : item; }
    private Map<String,AttributeValue> key(String sk) { return Map.of("pk",s(PK),"sk",s(sk)); }
    private Put put(Map<String,AttributeValue> item,String condition) { return Put.builder().tableName(table).item(item).conditionExpression(condition).build(); }
    private static Map<String,AttributeValue> messageItem(ConversationMessage m,String idem) { Map<String,AttributeValue> x=new HashMap<>();
        x.put("pk",s(PK)); x.put("sk",s(messageSk(m.sequence()))); x.put("itemType",s("MESSAGE")); x.put("messageId",s(m.messageId())); x.put("sequence",n(m.sequence()));
        x.put("role",s(m.role()==ConversationMessageRole.USER?"user":"assistant")); x.put("content",s(m.content())); x.put("createdAt",s(ConversationDateTime.format(m.createdAt()))); x.put("idempotencyKey",s(idem)); return x; }
    private static ConversationMessage message(Map<String,AttributeValue> m) { return new ConversationMessage(str(m,"messageId"),num(m,"sequence"),
            "user".equals(str(m,"role"))?ConversationMessageRole.USER:ConversationMessageRole.ASSISTANT,str(m,"content"),ConversationDateTime.parse(str(m,"createdAt"))); }
    private static String messageSk(long seq) { return "MESSAGE#%020d".formatted(seq); }
    private static AttributeValue s(String x) { return AttributeValue.builder().s(x).build(); }
    private static AttributeValue n(long x) { return AttributeValue.builder().n(Long.toString(x)).build(); }
    private static String str(Map<String,AttributeValue> m,String k) { return m.get(k).s(); }
    private static String nullable(Map<String,AttributeValue> m,String k) { return m.containsKey(k)?m.get(k).s():null; }
    private static long num(Map<String,AttributeValue> m,String k) { return Long.parseLong(m.get(k).n()); }
    private static long numOr(Map<String,AttributeValue> m,String k,long fallback) { return m.containsKey(k)?num(m,k):fallback; }
}
