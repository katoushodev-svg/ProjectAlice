package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStreamEvent;
import com.projectalice.backend.ai.application.model.AiExecutionContext;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.TextGenerationFinishReason;
import com.projectalice.backend.ai.application.model.TextGenerationRequest;
import com.projectalice.backend.ai.application.model.TextGenerationResult;
import com.projectalice.backend.ai.application.port.TextDeltaHandler;
import com.projectalice.backend.ai.application.port.TextGenerationProvider;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

final class OpenAiTextGenerationProvider implements TextGenerationProvider {

    private final OpenAiCallExecutor executor;
    private final OpenAiRequestMapper requestMapper;

    OpenAiTextGenerationProvider(OpenAiCallExecutor executor, OpenAiRequestMapper requestMapper) {
        this.executor = executor;
        this.requestMapper = requestMapper;
    }

    @Override
    public TextGenerationResult generate(
            TextGenerationRequest request,
            AiExecutionContext executionContext,
            TextDeltaHandler deltaHandler
    ) {
        // Set only after the delta was handed to the Application; once true, no further attempt is made.
        AtomicBoolean hasEmittedTextDelta = new AtomicBoolean(false);
        return executor.execute(
                executionContext,
                () -> !hasEmittedTextDelta.get(),
                (client, options) -> {
                    var params = requestMapper.toCreateParams(request);
                    StreamResponse<ResponseStreamEvent> stream =
                            client.responses().createStreaming(params, options);
                    AutoCloseable cancellationRegistration = null;
                    try {
                        cancellationRegistration = executionContext.cancellationToken()
                                .onCancellation(stream::close);
                        return consume(stream, executionContext, deltaHandler, hasEmittedTextDelta);
                    } finally {
                        closeQuietly(cancellationRegistration);
                        closeQuietly(stream);
                    }
                });
    }

    private TextGenerationResult consume(
            StreamResponse<ResponseStreamEvent> stream,
            AiExecutionContext context,
            TextDeltaHandler deltaHandler,
            AtomicBoolean hasEmittedTextDelta
    ) {
        StringBuilder accumulated = new StringBuilder();
        Iterator<ResponseStreamEvent> events = stream.stream().iterator();
        while (events.hasNext()) {
            ResponseStreamEvent event = events.next();
            executor.checkContext(context);

            if (event.isOutputTextDelta()) {
                String delta = event.asOutputTextDelta().delta();
                if (!delta.isEmpty()) {
                    deltaHandler.onDelta(delta);
                    hasEmittedTextDelta.set(true);
                    accumulated.append(delta);
                }
            } else if (event.isCompleted()) {
                return completed(event.asCompleted().response(), accumulated.toString(), context);
            } else if (event.isIncomplete()) {
                return incomplete(event.asIncomplete().response(), accumulated.toString(), context);
            } else if (event.isFailed() || event.isError()) {
                throw OpenAiFailureMapper.exception(AiFailureCategory.TEMPORARILY_UNAVAILABLE);
            }
        }
        executor.checkContext(context);
        throw OpenAiFailureMapper.exception(AiFailureCategory.PROTOCOL);
    }

    private TextGenerationResult completed(Response response, String streamed, AiExecutionContext context) {
        String text = outputText(response);
        if (text.isEmpty() || !text.equals(streamed)) {
            throw OpenAiFailureMapper.exception(AiFailureCategory.PROTOCOL);
        }
        executor.checkContext(context);
        return new TextGenerationResult(text, TextGenerationFinishReason.COMPLETED);
    }

    private TextGenerationResult incomplete(Response response, String streamed, AiExecutionContext context) {
        String reason = response.incompleteDetails()
                .flatMap(details -> details.reason())
                .map(r -> r.asString())
                .orElse("");
        if (reason.equals("max_output_tokens")) {
            String text = outputText(response);
            executor.checkContext(context);
            return new TextGenerationResult(
                    text.isEmpty() ? streamed : text,
                    TextGenerationFinishReason.OUTPUT_LIMIT_REACHED);
        }
        throw OpenAiFailureMapper.exception(reason.equals("content_filter")
                ? AiFailureCategory.UNEXPECTED
                : AiFailureCategory.PROTOCOL);
    }

    private static String outputText(Response response) {
        StringBuilder text = new StringBuilder();
        for (var item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (var content : message.content()) {
                content.outputText().ifPresent(part -> text.append(part.text()));
            }
        }
        return text.toString();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Closing is best effort; the outcome has already been decided.
        }
    }
}
