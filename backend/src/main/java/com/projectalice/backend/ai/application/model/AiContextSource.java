package com.projectalice.backend.ai.application.model;

/** Provider-independent classification for non-conversation context. */
public enum AiContextSource {
    PERSONAL_MEMORY,
    EXTERNAL_CONTENT,
    TOOL_RESULT,
    AGENT_OBSERVATION
}
