package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.inputtokens.InputTokenCountParams;
import com.projectalice.backend.ai.application.model.AiContextBlock;
import com.projectalice.backend.ai.application.model.AiMessage;
import com.projectalice.backend.ai.application.model.AiMessageRole;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import java.util.ArrayList;
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
        List<ResponseInputItem> items = new ArrayList<>(
                request.contextBlocks().size() + request.messages().size());
        for (AiContextBlock block : request.contextBlocks()) {
            items.add(toInputItem(block.content()));
        }
        for (AiMessage message : request.messages()) {
            items.add(toInputItem(message));
        }
        return List.copyOf(items);
    }

    private static ResponseInputItem toInputItem(AiMessage message) {
        EasyInputMessage.Role role = message.role() == AiMessageRole.USER
                ? EasyInputMessage.Role.USER
                : EasyInputMessage.Role.ASSISTANT;
        return toInputItem(role, message.content());
    }

    private static ResponseInputItem toInputItem(String contextContent) {
        return toInputItem(EasyInputMessage.Role.USER, contextContent);
    }

    private static ResponseInputItem toInputItem(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(content)
                .build());
    }
}
