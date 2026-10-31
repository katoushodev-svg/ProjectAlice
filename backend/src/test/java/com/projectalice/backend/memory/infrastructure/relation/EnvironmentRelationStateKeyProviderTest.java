package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentRelationStateKeyProviderTest {
    @Test void loadsOnlyConfiguredAes256VersionsAndProbesWithoutUsingProductionNonce() {
        String key1 = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] second = new byte[32]; second[0] = 1;
        String key2 = Base64.getEncoder().encodeToString(second);
        var provider = new EnvironmentRelationStateKeyProvider(Map.of(
                "ALICE_RELATION_STATE_WRITE_KEY_VERSION", "2",
                "ALICE_RELATION_STATE_KEY_V1_BASE64", key1,
                "ALICE_RELATION_STATE_KEY_V2_BASE64", key2));

        assertEquals(2, provider.activeEncryptionKey().version());
        assertEquals("AES", provider.decryptionKey(1).getAlgorithm());
        provider.probeRelationPurpose();
        assertThrows(RelationControlUnavailableException.class, () -> provider.decryptionKey(3));
    }

    @Test void failsClosedWhenWriteKeyIsMissingOrMalformed() {
        assertThrows(RelationControlUnavailableException.class,
                () -> new EnvironmentRelationStateKeyProvider(Map.of("ALICE_RELATION_STATE_WRITE_KEY_VERSION", "1")));
        assertThrows(RelationControlUnavailableException.class, () -> new EnvironmentRelationStateKeyProvider(Map.of(
                "ALICE_RELATION_STATE_WRITE_KEY_VERSION", "1",
                "ALICE_RELATION_STATE_KEY_V1_BASE64", Base64.getEncoder().encodeToString(new byte[16]))));
    }
}
