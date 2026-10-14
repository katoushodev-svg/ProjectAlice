package com.projectalice.backend.conversation.presentation;

import com.projectalice.backend.conversation.application.ConversationSendService;
import com.projectalice.backend.conversation.application.ConversationReadService;
import com.projectalice.backend.conversation.application.ConversationSendService.PreparedSend;
import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationDateTime;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/conversation")
public final class ConversationController {
    private static final MediaType EVENT_STREAM = MediaType.parseMediaType("text/event-stream");
    private final ConversationSendService send;
    private final ConversationReadService reads;
    private final Executor executor;
    private final Duration sseTimeout;
    private final ScheduledExecutorService heartbeatScheduler;

    public ConversationController(ConversationSendService send, ConversationReadService reads, Executor conversationSseExecutor,
            @Value("${alice.api.conversation.sse-timeout:180s}") Duration sseTimeout,
            @Value("${alice.api.conversation.heartbeat-interval:15s}") Duration heartbeatInterval,
            ScheduledExecutorService conversationHeartbeatScheduler) {
        this.send = send;
        this.reads = reads;
        this.executor = conversationSseExecutor;
        this.sseTimeout = sseTimeout;
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatScheduler = conversationHeartbeatScheduler;
    }
    private Duration heartbeatInterval;

    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> getConversation(HttpServletRequest request) {
        var conversation=reads.getConversation();
        Map<String,Object> body=new LinkedHashMap<>(); body.put("id",conversation.id());
        body.put("createdAt",ConversationDateTime.format(conversation.createdAt())); body.put("updatedAt",ConversationDateTime.format(conversation.updatedAt()));
        return json(body,request);
    }

    @GetMapping(path="/messages", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String,Object>> getMessages(@RequestParam(required=false) String limit,
            @RequestParam(required=false) String cursor,HttpServletRequest request) {
        Integer parsed=null;
        if(limit!=null) try { if(!limit.matches("^[0-9]+$")) throw new NumberFormatException(); parsed=Integer.valueOf(limit); }
        catch(NumberFormatException invalid) { throw new ConversationSendException("VALIDATION_ERROR",400,"limitは1から100で指定してください。"); }
        var page=reads.getMessages(parsed,cursor);
        return json(reads.toMap(page.messages(),page.nextCursor(),page.hasMore()),request);
    }

    private static ResponseEntity<Map<String,Object>> json(Map<String,Object> body,HttpServletRequest request) {
        String id=(String)request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header("X-Request-Id",id).body(body);
    }

    @PostMapping(path="/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SseEmitter> send(@RequestBody Map<String,Object> body,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (!acceptsEventStream(accept))
            throw new ConversationSendException("NOT_ACCEPTABLE", 406, "text/event-streamを受理してください。");
        if (key == null) throw new ConversationSendException("IDEMPOTENCY_KEY_REQUIRED", 400, "Idempotency-Keyが必要です。");
        try {
            if (!key.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) throw new IllegalArgumentException();
            UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            throw new ConversationSendException("INVALID_IDEMPOTENCY_KEY", 400, "Idempotency-KeyはUUID形式で指定してください。");
        }
        if (body == null) throw new ConversationSendException("MALFORMED_REQUEST", 400, "JSON Objectを指定してください。");
        if (!body.containsKey("content")) {
            throw validation("content","REQUIRED","contentは必須です。");
        }
        if (body.size() != 1) {
            String field = body.keySet().stream().filter(name -> !"content".equals(name)).findFirst().orElse("content");
            throw validation(field,"UNKNOWN_FIELD","未知のRequest Fieldです。");
        }
        Object contentValue = body.get("content");
        if (!(contentValue instanceof String content)) throw validation("content","INVALID_FORMAT","contentは文字列で指定してください。");
        if (content.isBlank()) throw validation("content","NOT_BLANK","メッセージを入力してください。");
        if (content.codePointCount(0, content.length()) > 10_000) throw validation("content","TOO_LONG","メッセージは10,000文字以内で入力してください。");
        PreparedSend prepared = send.prepare(key.toLowerCase(), content, requestId);
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        AtomicBoolean connected = new AtomicBoolean(true);
        ConversationSendService.ExecutionCancellation cancellation = new ConversationSendService.ExecutionCancellation();
        java.util.function.Consumer<ConversationSendService.StreamEvent> eventOutput = event -> {
            if (!connected.get()) return;
            try { emitter.send(SseEmitter.event().name(event.name()).data(payload(event.data()), MediaType.APPLICATION_JSON)); }
            catch (IOException disconnected) { connected.set(false); }
        };
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!connected.get()) return;
            try { emitter.send(SseEmitter.event().comment("heartbeat")); }
            catch (IOException disconnected) { connected.set(false); }
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> {
            send.timeout(prepared, cancellation, eventOutput);
            connected.set(false); heartbeat.cancel(false); emitter.complete();
        });
        try {
            executor.execute(() -> {
                try {
                    send.stream(prepared, eventOutput, cancellation);
                    if (connected.get()) emitter.complete();
                } catch (RuntimeException error) {
                    if (connected.get()) emitter.completeWithError(error);
                }
            });
        } catch (RejectedExecutionException rejected) {
            send.rejected(prepared, eventOutput);
            connected.set(false);
            heartbeat.cancel(false);
            emitter.complete();
        }
        return ResponseEntity.ok().contentType(EVENT_STREAM).header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Request-Id", requestId).body(emitter);
    }

    private static Map<String,Object> payload(Object data) {
        Map<String,Object> out = new LinkedHashMap<>();
        if (data instanceof ConversationSendService.Started e) { out.put("requestId",e.requestId()); out.put("userMessage",message(e.userMessage())); }
        else if (data instanceof ConversationSendService.Delta e) { out.put("requestId",e.requestId()); out.put("delta",e.delta()); }
        else if (data instanceof ConversationSendService.Completed e) { out.put("requestId",e.requestId()); out.put("userMessage",message(e.userMessage())); out.put("assistantMessage",message(e.assistantMessage())); }
        else if (data instanceof ConversationSendService.Failed e) { out.put("requestId",e.requestId()); out.put("code",e.code()); out.put("message",e.message()); }
        return out;
    }
    private static Map<String,Object> message(ConversationMessage message) {
        return Map.of("id",message.messageId(), "role",message.role().name().toLowerCase(), "content",message.content(), "createdAt",ConversationDateTime.format(message.createdAt()));
    }
    private static ConversationSendException validation(String field,String code,String detail) {
        return new ConversationSendException("VALIDATION_ERROR",400,detail,
                new ConversationSendException.FieldError(field,code,detail));
    }
    private static boolean acceptsEventStream(String header) {
        if (header == null) return false;
        try {
            return MediaType.parseMediaTypes(header).stream().anyMatch(type ->
                    type.getType().equalsIgnoreCase("text") && type.getSubtype().equalsIgnoreCase("event-stream")
                            && type.getQualityValue() > 0.0);
        } catch (IllegalArgumentException malformed) { return false; }
    }
}
