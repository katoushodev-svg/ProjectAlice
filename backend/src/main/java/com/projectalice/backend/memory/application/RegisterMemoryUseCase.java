package com.projectalice.backend.memory.application;

public interface RegisterMemoryUseCase {
    RegisterMemoryResult execute(RegisterMemoryCommand command);

    /** Entry point for API callers after request validation; key is never persisted verbatim. */
    default RegisterMemoryResult execute(String idempotencyKey, RegisterMemoryCommand command) {
        java.util.Objects.requireNonNull(command, "command must not be null");
        RegisterMemoryIdempotencyContext context = RegisterMemoryIdempotencyContext.forRequest(
                idempotencyKey, command.content().value(), command.category(), command.sensitivityLevel());
        return execute(command, context);
    }

    /**
     * Executes a registration with validated idempotency identity for a persistence adapter.
     * Implementations without durable idempotency storage must not claim replay guarantees.
     */
    default RegisterMemoryResult execute(RegisterMemoryCommand command,
            RegisterMemoryIdempotencyContext idempotencyContext) {
        java.util.Objects.requireNonNull(idempotencyContext, "idempotencyContext must not be null");
        throw new RegisterIdempotencyUnavailableException();
    }
}
