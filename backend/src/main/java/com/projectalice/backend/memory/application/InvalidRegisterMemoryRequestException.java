package com.projectalice.backend.memory.application;

/** Safe validation signal; excludes personal memory content from the exception message. */
public final class InvalidRegisterMemoryRequestException extends IllegalArgumentException {
    public InvalidRegisterMemoryRequestException() {
        super("Register request content is invalid");
    }
}
