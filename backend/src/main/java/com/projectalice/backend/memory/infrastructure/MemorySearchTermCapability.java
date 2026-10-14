package com.projectalice.backend.memory.infrastructure;

import com.projectalice.backend.memory.infrastructure.search.MemorySearchTermDigest;
import java.util.Optional;

/** Search-only capability gate result; absent or failed capability degrades Answer Search to unavailable. */
public record MemorySearchTermCapability(Optional<MemorySearchTermDigest> digest) {
    public MemorySearchTermCapability { digest = digest == null ? Optional.empty() : digest; }
}
