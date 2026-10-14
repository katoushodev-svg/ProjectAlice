package com.projectalice.backend.ai.application.model;

public enum AiFailureCategory {
    AUTHENTICATION_OR_CONFIGURATION,
    INVALID_REQUEST,
    RATE_LIMITED,
    TIMEOUT,
    TEMPORARILY_UNAVAILABLE,
    NETWORK,
    PROTOCOL,
    CANCELLED,
    UNEXPECTED
}
