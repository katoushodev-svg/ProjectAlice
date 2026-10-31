package com.projectalice.backend.memory.application.port.out;

import javax.crypto.SecretKey;

/** Dedicated secret boundary for MEMORY_* idempotency fingerprints.
 * Implementations must provide independently generated key and request-digest secrets;
 * an HSM-backed provider must preserve that separation even when key bytes are non-exportable.
 */
public interface MemoryIdempotencySecretProvider {
    SecretKey idempotencyKeyDigestSecret();
    SecretKey idempotencyRequestDigestSecret();
}
