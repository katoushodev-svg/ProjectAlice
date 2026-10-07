package com.projectalice.backend.ai.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiGenerationFailureTest {

    @Test
    void definesExactlyTheDesignedCategories() {
        assertEquals(
                List.of("AUTHENTICATION_OR_CONFIGURATION", "INVALID_REQUEST", "RATE_LIMITED",
                        "TIMEOUT", "TEMPORARILY_UNAVAILABLE", "NETWORK", "PROTOCOL",
                        "CANCELLED", "UNEXPECTED"),
                Arrays.stream(AiFailureCategory.values()).map(Enum::name).toList());
    }

    @Test
    void createsFailureForEveryCategory() {
        for (AiFailureCategory category : AiFailureCategory.values()) {
            AiGenerationFailure failure = new AiGenerationFailure(category, "safe", true);

            assertEquals(category, failure.category());
            assertEquals("safe", failure.safeMessage());
            assertTrue(failure.retryable());
        }
        assertFalse(new AiGenerationFailure(AiFailureCategory.PROTOCOL, "safe", false).retryable());
    }

    @Test
    void validatesRequiredValues() {
        assertThrows(NullPointerException.class, () -> new AiGenerationFailure(null, "safe", false));
        assertThrows(NullPointerException.class,
                () -> new AiGenerationFailure(AiFailureCategory.NETWORK, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new AiGenerationFailure(AiFailureCategory.NETWORK, " ", false));
    }

    @Test
    void failureIsImmutable() {
        assertTrue(Arrays.stream(AiGenerationFailure.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .allMatch(f -> Modifier.isFinal(f.getModifiers())));
        assertEquals(
                new AiGenerationFailure(AiFailureCategory.TIMEOUT, "safe", false),
                new AiGenerationFailure(AiFailureCategory.TIMEOUT, "safe", false));
    }

    @Test
    void exceptionCarriesFailureWithoutCause() {
        AiGenerationFailure failure = new AiGenerationFailure(AiFailureCategory.CANCELLED, "cancelled", false);

        AiGenerationException exception = new AiGenerationException(failure);

        assertTrue(exception instanceof RuntimeException);
        assertSame(failure, exception.failure());
        assertEquals("cancelled", exception.getMessage());
        assertNull(exception.getCause());
        assertThrows(NullPointerException.class, () -> new AiGenerationException(null));
    }

    @Test
    void exposesNoProviderSpecificTypes() {
        for (Class<?> type : List.of(AiGenerationFailure.class, AiGenerationException.class)) {
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> f.getType() == AiFailureCategory.class
                            || f.getType() == AiGenerationFailure.class
                            || f.getType() == String.class
                            || f.getType() == boolean.class));
        }
    }
}
