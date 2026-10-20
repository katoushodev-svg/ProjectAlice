package com.projectalice.backend.conversation.presentation;

import com.projectalice.backend.conversation.application.model.ConversationSendException;
import com.projectalice.backend.conversation.application.model.InvalidCursorException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ConversationSendException.class)
    ResponseEntity<Map<String,Object>> sendProblem(ConversationSendException exception, HttpServletRequest request) {
        String id = requestId(request);
        Map<String,Object> body = problem(exception.status(), exception.code(), exception.getMessage(), id);
        if (exception.fieldError() != null) body.put("errors", java.util.List.of(exception.fieldError()));
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("application/problem+json"));
        headers.setCacheControl("no-store"); headers.set("X-Request-Id", id);
        if ("REQUEST_IN_PROGRESS".equals(exception.code())) headers.set("Retry-After", "2");
        return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(exception.status()));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<Map<String,Object>> invalidCursor(InvalidCursorException exception,HttpServletRequest request) {
        String id=requestId(request); Map<String,Object> body=problem(400,"INVALID_CURSOR","Cursorが不正または解釈できません。",id);
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("application/problem+json")); headers.setCacheControl("no-store"); headers.set("X-Request-Id",id);
        return new ResponseEntity<>(body,headers,HttpStatusCode.valueOf(400));
    }

    @Override protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof PayloadTooLargeIOException)
                return mapped(413, "PAYLOAD_TOO_LARGE", "Request Bodyが上限を超えています。", request);
            cause = cause.getCause();
        }
        return mapped(400, "MALFORMED_REQUEST", "JSON Requestを読み取れませんでした。", request);
    }
    @Override protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return mapped(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Typeはapplication/jsonで指定してください。", request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> unexpected(Exception ex, HttpServletRequest request) {
        String id = requestId(request);
        Map<String,Object> body = problem(500,"INTERNAL_ERROR","処理中に予期しないエラーが発生しました。",id);
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("application/problem+json"));
        headers.setCacheControl("no-store"); headers.set("X-Request-Id",id);
        return new ResponseEntity<>(body,headers,HttpStatusCode.valueOf(500));
    }

    private ResponseEntity<Object> mapped(int status,String code,String detail,WebRequest request) {
        HttpServletRequest servlet = ((org.springframework.web.context.request.ServletWebRequest)request).getRequest();
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.parseMediaType("application/problem+json")); h.setCacheControl("no-store"); h.set("X-Request-Id",requestId(servlet));
        return new ResponseEntity<>(problem(status,code,detail,requestId(servlet)),h,HttpStatusCode.valueOf(status));
    }
    private static Map<String,Object> problem(int status,String code,String detail,String id) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("type","urn:project-alice:problem:"+code.toLowerCase().replace('_','-'));
        body.put("title", title(code)); body.put("status",status); body.put("detail",detail); body.put("code",code); body.put("requestId",id); return body;
    }
    private static String title(String code) { return switch(code) {
        case "VALIDATION_ERROR", "MALFORMED_REQUEST", "INVALID_IDEMPOTENCY_KEY", "IDEMPOTENCY_KEY_REQUIRED" -> "Validation failed";
        case "CONVERSATION_NOT_FOUND" -> "Conversation not found";
        case "NOT_ACCEPTABLE" -> "Not acceptable"; case "UNSUPPORTED_MEDIA_TYPE" -> "Unsupported media type";
        case "CONVERSATION_BUSY" -> "Conversation is busy"; case "REQUEST_IN_PROGRESS" -> "Request in progress";
        case "IDEMPOTENCY_KEY_CONFLICT" -> "Idempotency key conflict";
        case "PRECONDITION_REQUIRED" -> "Precondition required";
        case "PRECONDITION_FAILED" -> "Precondition failed";
        case "SERVICE_UNAVAILABLE" -> "Service unavailable";
        default -> "Internal error";
    }; }
    private static String requestId(HttpServletRequest request) {
        Object id=request.getAttribute(RequestIdFilter.ATTRIBUTE); return id == null ? java.util.UUID.randomUUID().toString() : id.toString();
    }
}
