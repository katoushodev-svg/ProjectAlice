package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.projectalice.backend.memory.application.RelationMutationPreview;
import com.projectalice.backend.memory.application.RelationReviewDraft;
import com.projectalice.backend.memory.application.RelatedMemorySearchResult;
import com.projectalice.backend.memory.application.RelationType;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacRelationTargetSetDigesterTest {
    private final HmacRelationTargetSetDigester digester = new HmacRelationTargetSetDigester(
            () -> new SecretKeySpec(new byte[32], "HmacSHA256"));

    @Test void digestIsStableAndBoundToOrderedTargetIdentityAndVersion() {
        var first = new RelationTargetSetDigester.TargetIdentity(0,
                "00000000-0000-4000-8000-000000000001", 3);
        var second = new RelationTargetSetDigester.TargetIdentity(1,
                "00000000-0000-4000-8000-000000000002", 4);
        String digest = digester.digestTargets(List.of(first, second));
        assertEquals(digest, digester.digestTargets(List.of(second, first)));
        assertNotEquals(digest, digester.digestTargets(List.of(first,
                new RelationTargetSetDigester.TargetIdentity(1, second.memoryId(), 5))));
        assertEquals(43, digest.length());
    }

    @Test void rejectsReuseOfRelationAeadKeyForTargetSetDigest() {
        var sameKey = new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256");
        var sameDigester = new HmacRelationTargetSetDigester(() -> sameKey);
        org.junit.jupiter.api.Assertions.assertThrows(RelationControlUnavailableException.class,
                () -> sameDigester.assertSeparateFrom(new javax.crypto.spec.SecretKeySpec(new byte[32], "AES")));
    }

    @Test void rejectsGappedOrdinals() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> digester.digestTargets(List.of(
                new RelationTargetSetDigester.TargetIdentity(1,
                        "00000000-0000-4000-8000-000000000002", 4))));
    }
}
