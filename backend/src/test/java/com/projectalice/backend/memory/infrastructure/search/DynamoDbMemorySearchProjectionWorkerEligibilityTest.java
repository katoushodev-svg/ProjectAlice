package com.projectalice.backend.memory.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DynamoDbMemorySearchProjectionWorkerEligibilityTest {
    @Test
    void allowsOnlyInitialCreateWithoutExistingMetadataOrFallback() {
        assertDoesNotThrow(() -> DynamoDbMemorySearchProjectionWorker
                .requireInitialCreateEligible(1, "CREATE", false));
        assertDoesNotThrow(() -> DynamoDbMemorySearchProjectionWorker.requireTargetRevisionEligible(2, "UPDATE"));
        assertDoesNotThrow(() -> DynamoDbMemorySearchProjectionWorker.requireNoFallback(false));

        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker
                .requireInitialCreateEligible(2, "UPDATE", false));
        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker
                .requireTargetRevisionEligible(1, "UPDATE"));
        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker
                .requireTargetRevisionEligible(2, "CREATE"));
        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker
                .requireInitialCreateEligible(1, "CREATE", true));
        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker
                .requireInitialCreateEligible(1, "UPDATE", false));
        assertThrows(IllegalStateException.class, () -> DynamoDbMemorySearchProjectionWorker.requireNoFallback(true));
    }
}
