package com.projectalice.backend.conversation.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.projectalice.backend.memory.application.InvalidRegisterIdempotencyKeyException;
import com.projectalice.backend.memory.application.InvalidRegisterMemoryRequestException;
import com.projectalice.backend.memory.application.RegisterMemoryIdempotencyKeyRequiredException;
import com.projectalice.backend.memory.application.RegisterIdempotencyUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RegisterIdempotencyProblemMappingTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test void missingKeyUsesExisting400ProblemContract() {
        assertMapped(new RegisterMemoryIdempotencyKeyRequiredException(), "IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test void malformedKeyUsesExisting400ProblemContract() {
        assertMapped(new InvalidRegisterIdempotencyKeyException(), "INVALID_IDEMPOTENCY_KEY");
    }

    @Test void invalidCanonicalContentUsesValidationProblemWithoutEchoingContent() {
        assertMapped(new InvalidRegisterMemoryRequestException(), "VALIDATION_ERROR");
    }

    @Test void unavailableBindingFailsClosedAsServiceUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "request-id");
        var response = handler.registerIdempotencyUnavailable(new RegisterIdempotencyUnavailableException(), request);
        @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("SERVICE_UNAVAILABLE", body.get("code"));
    }

    private void assertMapped(RuntimeException exception, String code) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "request-id");
        var response = handler.invalidRegisterRequest(exception, request);
        @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(400, response.getStatusCode().value());
        assertEquals(code, body.get("code"));
        assertEquals("request-id", body.get("requestId"));
    }
}
