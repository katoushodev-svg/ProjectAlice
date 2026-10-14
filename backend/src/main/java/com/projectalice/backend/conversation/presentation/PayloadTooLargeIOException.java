package com.projectalice.backend.conversation.presentation;

import java.io.IOException;

public final class PayloadTooLargeIOException extends IOException {
    public PayloadTooLargeIOException() { super("Request body exceeded the configured maximum."); }
}
