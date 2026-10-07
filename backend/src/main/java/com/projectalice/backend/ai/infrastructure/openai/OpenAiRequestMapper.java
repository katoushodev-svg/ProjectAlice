package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.inputtokens.InputTokenCountParams;
import com.projectalice.backend.ai.application.model.AiMessage;
import com.projectalice.backend.ai.application.model.AiMessageRole;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import java.util.List;

final class OpenAiRequestMapper {

    private final OpenAiSettings settings;

    OpenAiRequestMapper(OpenAiSettings settings) {
        this.settings = settings;
    }

    ResponseCreateParams toCreateParams(TextGenerationRequest request) {
        return ResponseCreateParams.builder()
                .model(settings.model())
                .instructions(request.instructions())
                .inputOfResponse(toInputItems(request))
                .reasoning(reasoning())
                .maxOutputTokens(settings.maxOutputTokens())
                .store(false)
                .build();
    }

    InputTokenCountParams toCountParams(TextGenerationRequest request) {
        return InputTokenCountParams.builder()
                .model(settings.model())
                .instructions(request.instructions())
                .inputOfResponseInputItems(toInputItems(request))
                .reasoning(reasoning())
                .build();
    }

    private Reasoning reasoning() {
        return Reasoning.builder().effort(ReasoningEffort.of(settings.reasoningEffort())).build();
    }

    private static List<ResponseInputItem> toInputItems(TextGenerationRequest request) {
        return request.messages().stream()
                .map(OpenAiRequestMapper::toInputItem)
                .toList();
    }

    private static ResponseInputItem toInputItem(AiMessage message) {
        EasyInputMessage.Role role = message.role() == AiMessageRole.USER
                ? EasyInputMessage.Role.USER
                : EasyInputMessage.Role.ASSISTANT;
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(message.content())
                .build());
    }
}
