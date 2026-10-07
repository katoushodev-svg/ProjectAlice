package com.projectalice.backend.ai.infrastructure.openai;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.SseException;
import com.projectalice.backend.ai.application.model.AiFailureCategory;
import com.projectalice.backend.ai.application.model.AiGenerationException;
import com.projectalice.backend.ai.application.model.AiGenerationFailure;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Optional;

/** Maps provider failures to fixed, sanitized Application failures. Raw messages are never exposed. */
final class OpenAiFailureMapper {

    private OpenAiFailureMapper() {
    }

    static AiGenerationException exception(AiFailureCategory category) {
        return new AiGenerationException(failure(category));
    }

    static AiGenerationFailure failure(AiFailureCategory category) {
        return failure(category, isRetryableCategory(category));
    }

    private static AiGenerationFailure failure(AiFailureCategory category, boolean retryable) {
        return new AiGenerationFailure(category, safeMessage(category), retryable);
    }

    static AiGenerationException map(Throwable throwable) {
        if (throwable instanceof AiGenerationException e) {
            return e;
        }
        if (throwable instanceof OpenAIServiceException e) {
            return new AiGenerationException(fromStatus(e.statusCode()));
        }
        if (throwable instanceof OpenAIIoException e) {
            return exception(isTimeout(e) ? AiFailureCategory.TIMEOUT : AiFailureCategory.NETWORK,
                    !isTimeout(e));
        }
        if (throwable instanceof OpenAIInvalidDataException || throwable instanceof SseException) {
            return exception(AiFailureCategory.PROTOCOL);
        }
        return exception(AiFailureCategory.UNEXPECTED);
    }

    /** Retry targets: network connection failure, HTTP 408, 429 and 5xx only. */
    static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof OpenAIServiceException e) {
            int status = e.statusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return throwable instanceof OpenAIIoException e && !isTimeout(e);
    }

    static Optional<Duration> retryAfter(Throwable throwable) {
        if (throwable instanceof OpenAIServiceException e) {
            for (String value : e.headers().values("retry-after")) {
                try {
                    long seconds = Long.parseLong(value.trim());
                    if (seconds >= 0) {
                        return Optional.of(Duration.ofSeconds(seconds));
                    }
                } catch (NumberFormatException ignored) {
                    // Non-numeric Retry-After values are not used.
                }
            }
        }
        return Optional.empty();
    }

    private static AiGenerationFailure fromStatus(int status) {
        AiFailureCategory category;
        if (status == 401 || status == 403) {
            category = AiFailureCategory.AUTHENTICATION_OR_CONFIGURATION;
        } else if (status == 408) {
            return failure(AiFailureCategory.TIMEOUT, true);
        } else if (status == 429) {
            category = AiFailureCategory.RATE_LIMITED;
        } else if (status >= 500) {
            category = AiFailureCategory.TEMPORARILY_UNAVAILABLE;
        } else if (status >= 400) {
            category = AiFailureCategory.INVALID_REQUEST;
        } else {
            category = AiFailureCategory.UNEXPECTED;
        }
        return failure(category);
    }

    private static AiGenerationException exception(AiFailureCategory category, boolean retryable) {
        return new AiGenerationException(failure(category, retryable));
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRetryableCategory(AiFailureCategory category) {
        return category == AiFailureCategory.RATE_LIMITED
                || category == AiFailureCategory.TEMPORARILY_UNAVAILABLE
                || category == AiFailureCategory.NETWORK;
    }

    private static String safeMessage(AiFailureCategory category) {
        return switch (category) {
            case AUTHENTICATION_OR_CONFIGURATION -> "AI provider authentication or configuration failed.";
            case INVALID_REQUEST -> "AI provider rejected the request.";
            case RATE_LIMITED -> "AI provider rate limit was reached.";
            case TIMEOUT -> "AI generation timed out.";
            case TEMPORARILY_UNAVAILABLE -> "AI provider is temporarily unavailable.";
            case NETWORK -> "Network error while contacting the AI provider.";
            case PROTOCOL -> "AI provider returned an unexpected response.";
            case CANCELLED -> "AI generation was cancelled.";
            case UNEXPECTED -> "Unexpected AI generation failure.";
        };
    }
}
