package com.projectalice.backend.conversation.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiMessage;
import com.projectalice.backend.ai.application.model.AiMessageRole;
import com.projectalice.backend.ai.application.model.AiWorkload;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationContextBuilderTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final AiExecutionContext EXECUTION_CONTEXT =
            new AiExecutionContext(Instant.parse("2026-01-01T00:00:00Z"), null);

    private final List<TextGenerationRequest> countedRequests = new ArrayList<>();
    private long tokenCount = 10;

    private ConversationContextBuilder builderWith(List<ConversationMessage> candidates) {
        ConversationRepository repository = limit -> candidates;
        return new ConversationContextBuilder(
                repository,
                (request, ctx) -> {
                    countedRequests.add(request);
                    return tokenCount;
                },
                new ConversationAiPolicy(64_000, 100, Duration.ofSeconds(60)));
    }

    private static ConversationMessage msg(String id, long seq, ConversationMessageRole role, String content) {
        return new ConversationMessage(id, seq, role, content, NOW);
    }

    @Test
    void buildsRequestWithoutHistory() {
        ConversationMessage current = msg("m1", 1, ConversationMessageRole.USER, "hello");

        TextGenerationRequest request = builderWith(List.of()).build("inst", current, EXECUTION_CONTEXT);

        assertThat(request.workload()).isEqualTo(AiWorkload.CONVERSATION_REPLY);
        assertThat(request.instructions()).isEqualTo("inst");
        assertThat(request.messages()).containsExactly(new AiMessage(AiMessageRole.USER, "hello"));
        assertThat(countedRequests).containsExactly(request);
    }

    @Test
    void ordersHistoryOldestFirstAndCurrentUserMessageLast() {
        // Repository returns sequence descending.
        List<ConversationMessage> candidates = List.of(
                msg("m3", 3, ConversationMessageRole.ASSISTANT, "a2"),
                msg("m2", 2, ConversationMessageRole.USER, "u2"),
                msg("m1", 1, ConversationMessageRole.ASSISTANT, "a1"));
        ConversationMessage current = msg("m4", 4, ConversationMessageRole.USER, "now");

        TextGenerationRequest request = builderWith(candidates).build("inst", current, EXECUTION_CONTEXT);

        assertThat(request.messages()).containsExactly(
                new AiMessage(AiMessageRole.ASSISTANT, "a1"),
                new AiMessage(AiMessageRole.USER, "u2"),
                new AiMessage(AiMessageRole.ASSISTANT, "a2"),
                new AiMessage(AiMessageRole.USER, "now"));
        assertThat(request.messages().getLast().role()).isEqualTo(AiMessageRole.USER);
    }

    @Test
    void removesCurrentMessageDuplicatedInHistoryByMessageId() {
        ConversationMessage current = msg("m2", 2, ConversationMessageRole.USER, "now");
        List<ConversationMessage> candidates = List.of(
                current,
                msg("m1", 1, ConversationMessageRole.ASSISTANT, "a1"));

        TextGenerationRequest request = builderWith(candidates).build("inst", current, EXECUTION_CONTEXT);

        assertThat(request.messages()).containsExactly(
                new AiMessage(AiMessageRole.ASSISTANT, "a1"),
                new AiMessage(AiMessageRole.USER, "now"));
    }

    @Test
    void excludesNullAndBlankContentHistory() {
        ConversationMessage nullContent = mock(ConversationMessage.class);
        when(nullContent.content()).thenReturn(null);
        ConversationMessage emptyContent = mock(ConversationMessage.class);
        when(emptyContent.content()).thenReturn("");
        ConversationMessage blankContent = mock(ConversationMessage.class);
        when(blankContent.content()).thenReturn("   ");
        List<ConversationMessage> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(nullContent);
        candidates.add(emptyContent);
        candidates.add(blankContent);
        candidates.add(msg("m1", 1, ConversationMessageRole.USER, "u1"));
        ConversationMessage current = msg("m2", 2, ConversationMessageRole.USER, "now");

        TextGenerationRequest request = builderWith(candidates).build("inst", current, EXECUTION_CONTEXT);

        assertThat(request.messages()).containsExactly(
                new AiMessage(AiMessageRole.USER, "u1"),
                new AiMessage(AiMessageRole.USER, "now"));
    }

    @Test
    void rejectsNonUserCurrentMessage() {
        ConversationMessage current = msg("m1", 1, ConversationMessageRole.ASSISTANT, "x");

        assertThatThrownBy(() -> builderWith(List.of()).build("inst", current, EXECUTION_CONTEXT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
