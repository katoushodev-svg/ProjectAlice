package com.projectalice.backend.conversation.application.model;

public final class InvalidCursorException extends RuntimeException {
    public InvalidCursorException() { super("Invalid cursor"); }
}
