package com.projectalice.backend.memory.presentation;

import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.presentation.RequestIdFilter;
import com.projectalice.backend.memory.application.GetMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.UpdateMemoryPreferencesUseCase;
import com.projectalice.backend.memory.application.port.out.MemoryPreferencesUpdateConflictException;
import com.projectalice.backend.memory.domain.MemoryPreferences;
import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memory-preferences")
public final class MemoryPreferencesController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final Pattern ETAG_PATTERN =
            Pattern.compile("^\"memory-preferences-v([1-9][0-9]*)\"$");

    private final GetMemoryPreferencesUseCase getPreferences;
    private final UpdateMemoryPreferencesUseCase updatePreferences;

    public MemoryPreferencesController(
            GetMemoryPreferencesUseCase getPreferences,
            UpdateMemoryPreferencesUseCase updatePreferences) {
        this.getPreferences = getPreferences;
        this.updatePreferences = updatePreferences;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request) {
        try {
            return response(getPreferences.execute(), request);
        } catch (IllegalStateException exception) {
            throw serviceUnavailable();
        }
    }

    @PatchMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> patch(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
            String ifMatch,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        long expectedVersion = parseIfMatch(ifMatch);
        validateBody(body);

        try {
            MemoryPreferences current = getPreferences.execute();

            boolean autoSaveEnabled = body.containsKey("autoSaveEnabled")
                    ? (Boolean) body.get("autoSaveEnabled")
                    : current.autoSaveEnabled();

            boolean answerUseEnabled = body.containsKey("answerUseEnabled")
                    ? (Boolean) body.get("answerUseEnabled")
                    : current.answerUseEnabled();

            MemoryPreferences updated = updatePreferences.execute(
                    autoSaveEnabled,
                    answerUseEnabled,
                    expectedVersion);

            String outcome = updated.version() == expectedVersion
                    ? "NO_CHANGE"
                    : "UPDATED";

            return mutationResponse(updated, outcome, request);
        } catch (MemoryPreferencesUpdateConflictException conflict) {
            throw new ConversationSendException(
                    "PRECONDITION_FAILED",
                    412,
                    "Memory Preferencesが更新されています。最新の設定を取得してください。");
        } catch (IllegalStateException exception) {
            throw serviceUnavailable();
        }
    }

    private static long parseIfMatch(String value) {
        if (value == null) {
            throw new ConversationSendException(
                    "PRECONDITION_REQUIRED",
                    428,
                    "If-Matchヘッダーが必要です。");
        }

        Matcher matcher = ETAG_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw validation(
                    "If-Match",
                    "INVALID_FORMAT",
                    "If-Matchには単一の有効なETagを指定してください。");
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException invalid) {
            throw validation(
                    "If-Match",
                    "INVALID_FORMAT",
                    "If-Matchのバージョンが不正です。");
        }
    }

    private static void validateBody(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw validation(
                    "body",
                    "REQUIRED",
                    "少なくとも1つの設定項目を指定してください。");
        }

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String field = entry.getKey();

            if (!"autoSaveEnabled".equals(field)
                    && !"answerUseEnabled".equals(field)) {
                throw validation(
                        field,
                        "UNKNOWN_FIELD",
                        "未知のRequest Fieldです。");
            }

            if (!(entry.getValue() instanceof Boolean)) {
                throw validation(
                        field,
                        "INVALID_FORMAT",
                        field + "はBooleanで指定してください。");
            }
        }
    }

    private static ResponseEntity<Map<String, Object>> response(
            MemoryPreferences preferences,
            HttpServletRequest request) {
        return baseResponse(preferences, request)
                .body(resource(preferences));
    }

    private static ResponseEntity<Map<String, Object>> mutationResponse(
            MemoryPreferences preferences,
            String outcome,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", outcome);
        body.put("preferences", resource(preferences));

        return baseResponse(preferences, request).body(body);
    }

    private static ResponseEntity.BodyBuilder baseResponse(
            MemoryPreferences preferences,
            HttpServletRequest request) {

        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String id = requestId == null
                ? UUID.randomUUID().toString()
                : requestId.toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Request-Id", id)
                .header(HttpHeaders.ETAG, etag(preferences.version()));
    }

    private static Map<String, Object> resource(
            MemoryPreferences preferences) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("autoSaveEnabled", preferences.autoSaveEnabled());
        body.put("answerUseEnabled", preferences.answerUseEnabled());
        body.put("version", preferences.version());
        body.put("etag", etag(preferences.version()));
        body.put("updatedAt", TIMESTAMP_FORMAT.format(preferences.updatedAt()));
        return body;
    }

    private static String etag(long version) {
        return "\"memory-preferences-v" + version + "\"";
    }

    private static ConversationSendException serviceUnavailable() {
        return new ConversationSendException(
                "SERVICE_UNAVAILABLE",
                503,
                "Memory Preferencesを取得または更新できません。時間をおいて再試行してください。");
    }

    private static ConversationSendException validation(
            String field,
            String code,
            String detail) {

        return new ConversationSendException(
                "VALIDATION_ERROR",
                400,
                detail,
                new ConversationSendException.FieldError(field, code, detail));
    }
}
