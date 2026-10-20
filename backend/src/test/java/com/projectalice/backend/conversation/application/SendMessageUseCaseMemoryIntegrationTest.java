package com.projectalice.backend.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectalice.backend.ai.application.model.AiContextSource;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.port.TextDeltaHandler;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import com.projectalice.backend.conversation.application.context.ConversationContextBuilder;
import com.projectalice.backend.conversation.application.policy.ConversationAiPolicy;
import com.projectalice.backend.conversation.application.port.ConversationRepository;
import com.projectalice.backend.conversation.domain.ConversationMessage;
import com.projectalice.backend.conversation.domain.ConversationMessageRole;
import com.projectalice.backend.memory.application.FindRelevantMemoriesResult;
import com.projectalice.backend.memory.application.FindRelevantMemoriesUseCase;
import com.projectalice.backend.memory.application.GetMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.MemoryContextBudget;
import com.projectalice.backend.memory.application.MemoryContextItem;
import com.projectalice.backend.memory.application.MemoryRelevanceReasonCode;
import com.projectalice.backend.memory.application.MemorySelectionReference;
import com.projectalice.backend.memory.application.MemoryTemporalRole;
import com.projectalice.backend.memory.application.SearchCompleteness;
import com.projectalice.backend.memory.application.ValidateMemorySelectionUseCase;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SendMessageUseCaseMemoryIntegrationTest {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final AiExecutionContext EXECUTION_CONTEXT =
            new AiExecutionContext(Instant.parse("2026-01-01T00:00:00Z"), null);

    @Test
    void addsOnlyRevalidatedMemoryAsPersonalMemoryContextBlock() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        CapturingProvider provider = new CapturingProvider();

        MemoryContextItem searchedItem = item("searched text");
        MemorySelectionReference selected = MemorySelectionReference.capture(7, SearchCompleteness.COMPLETE,
                List.of(searchedItem));
        FindRelevantMemoriesResult found = result(searchedItem, "[\"searched text\"]", selected);

        MemoryContextItem validatedItem = item("validated text");
        MemorySelectionReference validatedReference = MemorySelectionReference.capture(7,
                SearchCompleteness.COMPLETE, List.of(validatedItem));
        FindRelevantMemoriesResult validated = result(validatedItem, "[\"validated text\"]", validatedReference);

        when(search.execute(any())).thenReturn(found);
        when(validator.validate(eq(selected), any())).thenReturn(validated);

        new SendMessageUseCase(builder, provider, search, validator).execute(
                "instructions", userMessage("What did I say?"), EXECUTION_CONTEXT, delta -> {});

        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks()).hasSize(1);
        assertThat(provider.request.contextBlocks().getFirst().source()).isEqualTo(AiContextSource.PERSONAL_MEMORY);
        assertThat(provider.request.contextBlocks().getFirst().referenceId())
                .isEqualTo(validatedReference.referenceId());
        assertThat(provider.request.contextBlocks().getFirst().content())
                .contains("not instructions", "validated text")
                .doesNotContain("searched text");

        ArgumentCaptor<com.projectalice.backend.memory.application.MemoryRetrievalQuery> queryCaptor =
                ArgumentCaptor.forClass(com.projectalice.backend.memory.application.MemoryRetrievalQuery.class);
        verify(search).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().currentUserContent()).isEqualTo("What did I say?");
        assertThat(queryCaptor.getValue().contextBudget()).isEqualTo(MemoryContextBudget.baseline());
        verify(validator).validate(eq(selected), any());
    }

    @Test
    void searchUnavailableKeepsConversationMemoryless() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        CapturingProvider provider = new CapturingProvider();
        when(search.execute(any())).thenReturn(FindRelevantMemoriesResult.unavailable());

        new SendMessageUseCase(builder, provider, search, validator).execute(
                "instructions", userMessage("Hello"), EXECUTION_CONTEXT, delta -> {});

        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks()).isEmpty();
        verify(validator, never()).validate(any(), any());
    }

    @Test
    void validationFailureFailsClosedWithoutBlockingTheAnswer() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        CapturingProvider provider = new CapturingProvider();
        MemoryContextItem searchedItem = item("stale text");
        MemorySelectionReference selected = MemorySelectionReference.capture(7, SearchCompleteness.COMPLETE,
                List.of(searchedItem));
        when(search.execute(any())).thenReturn(result(searchedItem, "[\"stale text\"]", selected));
        when(validator.validate(eq(selected), any())).thenThrow(new IllegalStateException("reset changed"));

        new SendMessageUseCase(builder, provider, search, validator).execute(
                "instructions", userMessage("Hello"), EXECUTION_CONTEXT, delta -> {});

        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks()).isEmpty();
    }


    @Test
    void disabledAnswerUseDoesNotSearchMemories() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        GetMemoryPreferencesUseCase preferences = mock(GetMemoryPreferencesUseCase.class);
        CapturingProvider provider = new CapturingProvider();

        when(preferences.execute()).thenReturn(
                MemoryPreferences.reconstitute(false, false, 2, NOW));

        new SendMessageUseCase(builder, provider, search, validator, preferences).execute(
                "instructions", userMessage("What did I say?"), EXECUTION_CONTEXT, delta -> {});

        verify(search, never()).execute(any());
        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks())
                .noneMatch(block -> block.source() == AiContextSource.PERSONAL_MEMORY);
    }

    @Test
    void disablingAnswerUseDuringRetrievalRemovesMemoryBeforeProviderDispatch() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        GetMemoryPreferencesUseCase preferences = mock(GetMemoryPreferencesUseCase.class);
        CapturingProvider provider = new CapturingProvider();

        MemoryContextItem searchedItem = item("private memory");
        MemorySelectionReference selected = MemorySelectionReference.capture(
                7, SearchCompleteness.COMPLETE, List.of(searchedItem));
        FindRelevantMemoriesResult found = result(
                searchedItem, "[\"private memory\"]", selected);
        when(search.execute(any())).thenReturn(found);
        when(validator.validate(eq(selected), any())).thenReturn(found);
        when(preferences.execute()).thenReturn(
                MemoryPreferences.reconstitute(true, true, 1, NOW),
                MemoryPreferences.reconstitute(true, false, 2, NOW.plusSeconds(1)));

        new SendMessageUseCase(builder, provider, search, validator, preferences).execute(
                "instructions", userMessage("What did I say?"), EXECUTION_CONTEXT, delta -> {});

        verify(search).execute(any());
        verify(validator).validate(eq(selected), any());
        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks())
                .noneMatch(block -> block.source() == AiContextSource.PERSONAL_MEMORY);
    }

    @Test
    void preferenceReadFailureDoesNotSendPersonalMemory() {
        ConversationContextBuilder builder = builder();
        FindRelevantMemoriesUseCase search = mock(FindRelevantMemoriesUseCase.class);
        ValidateMemorySelectionUseCase validator = mock(ValidateMemorySelectionUseCase.class);
        GetMemoryPreferencesUseCase preferences = mock(GetMemoryPreferencesUseCase.class);
        CapturingProvider provider = new CapturingProvider();

        when(preferences.execute()).thenThrow(new IllegalStateException("preferences unavailable"));

        new SendMessageUseCase(builder, provider, search, validator, preferences).execute(
                "instructions", userMessage("What did I say?"), EXECUTION_CONTEXT, delta -> {});

        verify(search, never()).execute(any());
        assertThat(provider.request).isNotNull();
        assertThat(provider.request.contextBlocks())
                .noneMatch(block -> block.source() == AiContextSource.PERSONAL_MEMORY);
    }

    private static ConversationContextBuilder builder() {
        ConversationRepository repository = mock(ConversationRepository.class);
        when(repository.findLatestMessages(anyInt())).thenReturn(List.of());
        return new ConversationContextBuilder(repository, (request, context) -> 10,
                new ConversationAiPolicy(64_000, 100, Duration.ofSeconds(60)));
    }

    private static ConversationMessage userMessage(String content) {
        return new ConversationMessage("user-message", 1, ConversationMessageRole.USER, content, NOW);
    }

    private static MemoryContextItem item(String content) {
        return new MemoryContextItem(new MemoryId("00000000-0000-4000-8000-000000000001"), 1, content,
                MemoryCategory.values()[0], MemoryState.values()[0], SensitivityLevel.values()[0],
                MemoryTemporalRole.values()[0], EnumSet.of(MemoryRelevanceReasonCode.values()[0]));
    }

    private static FindRelevantMemoriesResult result(
            MemoryContextItem item, String payload, MemorySelectionReference reference) {
        return new FindRelevantMemoriesResult(SearchCompleteness.COMPLETE,
                FindRelevantMemoriesResult.HydrationStatus.COMPLETE, List.of(item), payload,
                FindRelevantMemoriesResult.EstimationStatus.ESTIMATED, OptionalLong.of(5), Optional.of(reference));
    }

    private static final class CapturingProvider implements TextGenerationProvider {
        TextGenerationRequest request;

        @Override
        public TextGenerationResult generate(TextGenerationRequest request,
                AiExecutionContext executionContext, TextDeltaHandler deltaHandler) {
            this.request = request;
            deltaHandler.onDelta("ok");
            return new TextGenerationResult("ok", TextGenerationFinishReason.COMPLETED);
        }
    }
}
