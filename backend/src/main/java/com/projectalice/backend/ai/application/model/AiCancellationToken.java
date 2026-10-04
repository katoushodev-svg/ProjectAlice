package com.projectalice.backend.ai.application.model;

public interface AiCancellationToken {

    boolean isCancellationRequested();

    AutoCloseable onCancellation(Runnable callback);
}
