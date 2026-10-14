package com.projectalice.backend.conversation.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.port.InputTokenCounter;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="ALICE_CURSOR_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class ConversationHttpIntegrationTest {
    private static final String KEY="a0b73d9c-1fd3-49c0-b818-e271f3137064";
    @Value("${local.server.port}") int port;
    @MockitoBean ConversationRepository repository;
    @MockitoBean InputTokenCounter tokenCounter;
    @MockitoBean TextGenerationProvider provider;

    @BeforeEach void configureFakes() {
        when(repository.findIdempotency(any())).thenReturn(Optional.empty());
        when(repository.findConversation()).thenReturn(Optional.empty());
        when(repository.findLatestMessages(100)).thenReturn(List.of());
        when(tokenCounter.count(any(),any())).thenReturn(4L);
        doAnswer(invocation -> {
            invocation.getArgument(2,com.projectalice.backend.ai.application.port.TextDeltaHandler.class).onDelta("Aliceの回答");
            return new TextGenerationResult("Aliceの回答",TextGenerationFinishReason.COMPLETED);
        }).when(provider).generate(any(),any(),any());
    }

    @Test void postStreamsStartedDeltaAndCanonicalCompletionWithOneRequestId() throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream")
                .header("Idempotency-Key",KEY).POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
        String id=response.headers().firstValue("X-Request-Id").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("event:stream.started","event:assistant.delta","event:assistant.completed",
                "\"userMessage\"","\"assistantMessage\"",id);
    }

    @Test void validationFailureIsProblemDetailsBeforeSseStarts() throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"  \"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"","\"requestId\"");
    }

    @Test void malformedJsonIsProblemDetails() throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
    }

    @Test void getConversationReturnsContractFieldsAndNotFoundWhenAbsent() throws Exception {
        var absent=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation")).GET().build();
        var missing=HttpClient.newHttpClient().send(absent,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(missing.statusCode()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(missing.body()).contains("CONVERSATION_NOT_FOUND");
        when(repository.findConversation()).thenReturn(Optional.of(new ConversationRepository.ConversationState("conversation-id",
                OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9)),OffsetDateTime.of(2026,8,14,15,5,30,0,ZoneOffset.ofHours(9)),1,null,0)));
        var response=HttpClient.newHttpClient().send(absent,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("\"id\":\"conversation-id\"","\"createdAt\"","\"updatedAt\"").doesNotContain("lastSequence");
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
    }

    @Test void getMessagesReturnsEmptyPageWhenConversationDoesNotExist() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages?limit=50")).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("\"messages\":[]","\"nextCursor\":null","\"hasMore\":false");
    }

    @Test void invalidCursorIsRejectedWithoutReadingConversationOrMessages() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages?cursor=invalid" )).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("INVALID_CURSOR");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findConversation();
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findLatestMessages(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findMessagesBefore(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyInt());
    }

    @Test void inProgressRetryIsRejectedBeforeSseWithRetryAfter() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        var user=new com.projectalice.backend.conversation.domain.ConversationMessage("m1",1,
                com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"hello",at);
        when(repository.findIdempotency(KEY)).thenReturn(Optional.of(new ConversationRepository.IdempotencyState(
                KEY,"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824","PROCESSING",user,null,null,Long.MAX_VALUE,0)));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("Retry-After").orElse("")).isEqualTo("2");
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("REQUEST_IN_PROGRESS");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).start(any());
    }

    @Test void differentContentWithExistingIdempotencyKeyReturnsConflict() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        var user=new com.projectalice.backend.conversation.domain.ConversationMessage("m1",1,
                com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"hello",at);
        when(repository.findIdempotency(KEY)).thenReturn(Optional.of(new ConversationRepository.IdempotencyState(
                KEY,"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824","COMPLETED",user,null,null,0,Long.MAX_VALUE)));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"changed\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("IDEMPOTENCY_KEY_CONFLICT");
        org.assertj.core.api.Assertions.assertThat(response.headers().firstValue("Retry-After")).isEmpty();
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).start(any());
    }

    @Test void failedSendReplayReturnsCanonicalStartedAndFailedWithoutRegeneration() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        var user=new com.projectalice.backend.conversation.domain.ConversationMessage("m1",1,
                com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"hello",at);
        when(repository.findIdempotency(KEY)).thenReturn(Optional.of(new ConversationRepository.IdempotencyState(
                KEY,"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824","FAILED",user,null,
                "RESPONSE_GENERATION_FAILED",0,Long.MAX_VALUE)));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("event:stream.started","\"id\":\"m1\"",
                "event:stream.failed","RESPONSE_GENERATION_FAILED").doesNotContain("event:assistant.delta");
        org.mockito.Mockito.verify(provider,org.mockito.Mockito.never()).generate(any(),any(),any());
    }

    @Test void completedSendReplayReturnsCanonicalStartedAndCompletedWithoutRegeneration() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        var user=new com.projectalice.backend.conversation.domain.ConversationMessage("m1",1,
                com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"hello",at);
        var assistant=new com.projectalice.backend.conversation.domain.ConversationMessage("m2",2,
                com.projectalice.backend.conversation.domain.ConversationMessageRole.ASSISTANT,"saved answer",at.plusSeconds(1));
        when(repository.findIdempotency(KEY)).thenReturn(Optional.of(new ConversationRepository.IdempotencyState(
                KEY,"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824","COMPLETED",user,assistant,null,0,Long.MAX_VALUE)));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String requestId=response.headers().firstValue("X-Request-Id").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("event:stream.started","\"id\":\"m1\"",
                "event:assistant.completed","\"id\":\"m2\"","saved answer",requestId).doesNotContain("event:assistant.delta");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).start(any());
        org.mockito.Mockito.verify(provider,org.mockito.Mockito.never()).generate(any(),any(),any());
    }

    @Test void activeConversationRejectsDifferentSendAsBusy() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        when(repository.findConversation()).thenReturn(Optional.of(new ConversationRepository.ConversationState(
                "conversation-id",at,at,1,"another-key",Long.MAX_VALUE)));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("CONVERSATION_BUSY");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).start(any());
    }

    @Test void postBodyAbove128KiBIsRejectedBeforeRepositoryAccess() throws Exception {
        String prefix="{\"content\":\""; String suffix="\"}";
        String body=prefix+"x".repeat(131_073-prefix.length()-suffix.length())+suffix;
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(413);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("PAYLOAD_TOO_LARGE");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findIdempotency(any());
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).start(any());
    }

    @Test void postBodyAt128KiBPassesBodyLimitAndReachesRequestValidation() throws Exception {
        String prefix="{\"padding\":\""; String suffix="\"}";
        String body=prefix+"x".repeat(131_072-prefix.length()-suffix.length())+suffix;
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.body()).contains("VALIDATION_ERROR").doesNotContain("PAYLOAD_TOO_LARGE");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findIdempotency(any());
    }

    @Test void disconnectStopsSseDeliveryButGenerationStillPersists() throws Exception {
        CountDownLatch generationPaused=new CountDownLatch(1), releaseGeneration=new CountDownLatch(1), persisted=new CountDownLatch(1);
        String generated="partial".repeat(1_200);
        doAnswer(invocation -> {
            invocation.getArgument(2,com.projectalice.backend.ai.application.port.TextDeltaHandler.class).onDelta(generated);
            generationPaused.countDown();
            if (!releaseGeneration.await(3,TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
            return new TextGenerationResult(generated,TextGenerationFinishReason.COMPLETED);
        }).when(provider).generate(any(),any(),any());
        org.mockito.Mockito.doAnswer(invocation -> { persisted.countDown(); return null; }).when(repository).complete(any(),any());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages"))
                .header("Content-Type","application/json").header("Accept","text/event-stream").header("Idempotency-Key",KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"hello\"}")).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofInputStream());
        try (var reader=new BufferedReader(new InputStreamReader(response.body()))) {
            String line; boolean deltaSeen=false;
            while ((line=reader.readLine())!=null) if (line.startsWith("event:") && line.contains("assistant.delta")) { deltaSeen=true; break; }
            org.assertj.core.api.Assertions.assertThat(deltaSeen).isTrue();
            org.assertj.core.api.Assertions.assertThat(generationPaused.await(2,TimeUnit.SECONDS)).isTrue();
        } finally { releaseGeneration.countDown(); }
        org.assertj.core.api.Assertions.assertThat(persisted.await(2,TimeUnit.SECONDS)).isTrue();
        org.mockito.Mockito.verify(repository).complete(any(),any());
    }

    @Test void invalidCursorRejectsDecimalVersionAndSequenceBeforeRepositoryAccess() throws Exception {
        for (String payload : List.of(
                "{\"v\":1.0,\"conversationId\":\"conversation-id\",\"exclusiveStartSequence\":1}",
                "{\"v\":1,\"conversationId\":\"conversation-id\",\"exclusiveStartSequence\":1.5}")) {
            byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), "HmacSHA256"));
            String cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes) + "."
                    + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payloadBytes));
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/conversation/messages?cursor=" + cursor)).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(400);
            org.assertj.core.api.Assertions.assertThat(response.body()).contains("INVALID_CURSOR");
        }
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findConversation();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findLatestMessages(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findMessagesBefore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test void getMessagesRejectsOutOfRangeAndMalformedLimitsWithoutRepositoryAccess() throws Exception {
        for (String limit : List.of("0","101","abc")) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages?limit="+limit)).GET().build();
            var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
            org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(400);
            org.assertj.core.api.Assertions.assertThat(response.body()).contains("VALIDATION_ERROR");
        }
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).findConversation();
    }

    @Test void getMessagesPagesNewestFirstAndReturnsEachPageInAscendingOrder() throws Exception {
        var at=OffsetDateTime.of(2026,8,14,15,0,0,0,ZoneOffset.ofHours(9));
        when(repository.findConversation()).thenReturn(Optional.of(new ConversationRepository.ConversationState("conversation-id",at,at,3,null,0)));
        var one=new com.projectalice.backend.conversation.domain.ConversationMessage("m1",1,com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"one",at);
        var two=new com.projectalice.backend.conversation.domain.ConversationMessage("m2",2,com.projectalice.backend.conversation.domain.ConversationMessageRole.ASSISTANT,"two",at.plusSeconds(1));
        var three=new com.projectalice.backend.conversation.domain.ConversationMessage("m3",3,com.projectalice.backend.conversation.domain.ConversationMessageRole.USER,"three",at.plusSeconds(2));
        when(repository.findLatestMessages(3)).thenReturn(List.of(three,two,one));
        when(repository.findMessagesBefore(2,3)).thenReturn(List.of(one));
        var firstRequest=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages?limit=2")).GET().build();
        var first=HttpClient.newHttpClient().send(firstRequest,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(first.statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(first.body()).contains("\"id\":\"m2\"","\"id\":\"m3\"","\"hasMore\":true");
        org.assertj.core.api.Assertions.assertThat(first.body().indexOf("m2")).isLessThan(first.body().indexOf("m3"));
        String cursor=java.util.regex.Pattern.compile("\\\"nextCursor\\\":\\\"([^\\\"]+)\\\"").matcher(first.body()).results().findFirst().orElseThrow().group(1);
        var nextRequest=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/conversation/messages?limit=2&cursor="+cursor)).GET().build();
        var next=HttpClient.newHttpClient().send(nextRequest,HttpResponse.BodyHandlers.ofString());
        org.assertj.core.api.Assertions.assertThat(next.statusCode()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(next.body()).contains("\"id\":\"m1\"","\"nextCursor\":null","\"hasMore\":false");
    }
}
