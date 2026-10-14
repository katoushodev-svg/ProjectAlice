package com.projectalice.backend.memory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.knuddels.jtokkit.api.Encoding;
import com.projectalice.backend.memory.application.TokenEstimationResult;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class JTokkitTokenEstimatorTest {

    private final JTokkitTokenEstimator estimator = new JTokkitTokenEstimator();

    @Test
    void estimatesJapaneseEnglishEmojiJsonWhitespaceAndLongPayloads() {
        for (String payload : new String[] {
            "札幌に住んでいる。設計を重視して開発する。",
            "I prefer simple and maintainable designs.",
            "[\"今日は 🙂 🚀 🧠 を使った。\"]",
            "[\"quote=\\\" slash=\\\\ line=\\n\"]",
            "[\"  leading and trailing  \",\"e\\u0301\"]",
            "[\"長い識別子_" + "ProjectAliceTokenEstimator".repeat(100) + "\"]"
        }) {
            TokenEstimationResult result = estimator.estimate(payload);
            TokenEstimationResult.Estimated estimated = assertInstanceOf(TokenEstimationResult.Estimated.class, result);
            assertTrue(estimated.tokenCount() > 0);
            assertEquals(result, estimator.estimate(payload));
        }
    }

    @Test
    void estimatesEmptyPayloadAsZeroTokens() {
        assertEquals(new TokenEstimationResult.Estimated(0), estimator.estimate(""));
    }

    @Test
    void convertsRuntimeTokenizerFailureToUnavailable() {
        Encoding brokenEncoding = (Encoding) Proxy.newProxyInstance(
                Encoding.class.getClassLoader(),
                new Class<?>[] {Encoding.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("countTokens")) {
                        throw new IllegalStateException("failed");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        JTokkitTokenEstimator failingEstimator = new JTokkitTokenEstimator(brokenEncoding);

        assertEquals(new TokenEstimationResult.Unavailable(), failingEstimator.estimate("payload"));
    }
}
