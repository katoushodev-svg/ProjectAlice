package com.projectalice.backend.conversation.application.model;

public final class ConversationSendException extends RuntimeException {
    private final String code;
    private final int status;
    private final FieldError fieldError;

    public ConversationSendException(String code, int status, String message) {
        this(code,status,message,null);
    }
    public ConversationSendException(String code, int status, String message, FieldError fieldError) {
        super(message);
        this.code = code;
        this.status = status;
        this.fieldError = fieldError;
    }

    public String code() { return code; }
    public int status() { return status; }
    public FieldError fieldError() { return fieldError; }
    public record FieldError(String field, String code, String message) { }
}
