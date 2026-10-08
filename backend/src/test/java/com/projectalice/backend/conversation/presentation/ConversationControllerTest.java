package com.projectalice.backend.conversation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectalice.backend.conversation.application.ConversationSendService;
import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.application.port.ConversationRepository.IdempotencyState;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class ConversationControllerTest {
    private static final String KEY="a0b73d9c-1fd3-49c0-b818-e271f3137064";
    private static final String REQUEST_ID="f1138625-44d8-4946-b665-89a737c577d6";
    private final ConversationSendService send=mock(ConversationSendService.class);
    private final com.projectalice.backend.conversation.application.ConversationReadService reads =
            new com.projectalice.backend.conversation.application.ConversationReadService(mock(com.projectalice.backend.conversation.application.port.ConversationRepository.class),new byte[32]);
    private final java.util.concurrent.ScheduledExecutorService scheduler=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread=new Thread(task,"test-heartbeat"); thread.setDaemon(true); return thread;
    });
    private final ConversationController controller=new ConversationController(send,reads,(Executor)Runnable::run,
            Duration.ofSeconds(180),Duration.ofSeconds(15),scheduler);

    private MockHttpServletRequest request() {
        MockHttpServletRequest request=new MockHttpServletRequest(); request.setAttribute(RequestIdFilter.ATTRIBUTE,REQUEST_ID); return request;
    }

    @Test void acceptsValidPostAndReturnsSseResponseWithBackendRequestId() throws Exception {
        ConversationMessage user=new ConversationMessage("user-id",1,ConversationMessageRole.USER,"hello",
                OffsetDateTime.of(2026,1,1,0,0,0,0, ZoneOffset.ofHours(9)));
        var prepared=new ConversationSendService.PreparedSend(KEY,"hello",REQUEST_ID,
                new IdempotencyState(KEY,keyHash(),"PROCESSING",user,null,null,0,0),true);
        when(send.prepare(KEY,"hello",REQUEST_ID)).thenReturn(prepared);
        doAnswer(invocation -> null).when(send).stream(eq(prepared),any());
        var response=controller.send(java.util.Map.of("content","hello"),"text/event-stream",KEY,request());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("text/event-stream"));
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(REQUEST_ID);
        verify(send).prepare(KEY,"hello",REQUEST_ID);
    }

    @Test void rejectsWhitespaceContentBeforeStartingIdempotency() {
        assertThatThrownBy(() -> controller.send(java.util.Map.of("content"," \n\t"),"text/event-stream",KEY,request()))
                .isInstanceOf(ConversationSendException.class).hasMessageContaining("メッセージを入力");
    }

    @Test void rejectsMalformedIdempotencyAndMissingSseAcceptBeforeStarting() throws Exception {
        var body=java.util.Map.<String,Object>of("content","hello");
        assertThatThrownBy(() -> controller.send(body,"text/event-stream","not-a-uuid",request()))
                .isInstanceOf(ConversationSendException.class).extracting("code").isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThatThrownBy(() -> controller.send(body,"application/json",KEY,request()))
                .isInstanceOf(ConversationSendException.class).extracting("code").isEqualTo("NOT_ACCEPTABLE");
    }

    @Test void rejectsUnknownFieldsAndNonStringContent() {
        assertThatThrownBy(() -> controller.send(java.util.Map.of("content","hello","role","user"),"text/event-stream",KEY,request()))
                .isInstanceOf(ConversationSendException.class).extracting("fieldError.field").isEqualTo("role");
        assertThatThrownBy(() -> controller.send(java.util.Map.of("content",3),"text/event-stream",KEY,request()))
                .isInstanceOf(ConversationSendException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
    }

    @Test void executorRejectionPersistsTerminalFailureInsteadOfLeavingProcessing() throws Exception {
        ConversationMessage user=new ConversationMessage("user-id",1,ConversationMessageRole.USER,"hello",
                OffsetDateTime.of(2026,1,1,0,0,0,0, ZoneOffset.ofHours(9)));
        var prepared=new ConversationSendService.PreparedSend(KEY,"hello",REQUEST_ID,
                new IdempotencyState(KEY,keyHash(),"PROCESSING",user,null,null,0,0),true);
        when(send.prepare(KEY,"hello",REQUEST_ID)).thenReturn(prepared);
        ConversationController rejectingController=new ConversationController(send,reads,
                command -> { throw new RejectedExecutionException("full"); },Duration.ofSeconds(180),Duration.ofSeconds(15),scheduler);
        var response=rejectingController.send(java.util.Map.of("content","hello"),"text/event-stream",KEY,request());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(send).rejected(eq(prepared),any());
    }

    private static String keyHash() { return "hash"; }
}
