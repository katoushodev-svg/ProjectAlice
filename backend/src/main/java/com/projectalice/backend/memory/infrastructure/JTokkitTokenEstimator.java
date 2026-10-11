package com.projectalice.backend.memory.infrastructure;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.projectalice.backend.memory.application.TokenEstimationResult;
import com.projectalice.backend.memory.application.port.TokenEstimator;
import java.util.Objects;

/** Local token estimate implementation using JTokkit's explicitly selected o200k_base encoding. */
public final class JTokkitTokenEstimator implements TokenEstimator {

    private final Encoding encoding;

    public JTokkitTokenEstimator() {
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.O200K_BASE);
    }

    JTokkitTokenEstimator(Encoding encoding) {
        this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    }

    @Override
    public TokenEstimationResult estimate(String payload) {
        try {
            return new TokenEstimationResult.Estimated(encoding.countTokens(payload));
        } catch (RuntimeException exception) {
            return new TokenEstimationResult.Unavailable();
        }
    }
}
