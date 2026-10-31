package com.projectalice.backend.memory.infrastructure.relation;

import javax.crypto.SecretKey;

/** Dedicated, purpose-separated HMAC secret for Relation target-set integrity digests. */
public interface RelationTargetSetDigestKeyProvider {
    SecretKey targetSetDigestKey();
}
