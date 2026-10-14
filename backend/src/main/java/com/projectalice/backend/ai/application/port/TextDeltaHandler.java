package com.projectalice.backend.ai.application.port;

@FunctionalInterface
public interface TextDeltaHandler {

    void onDelta(String delta);
}
