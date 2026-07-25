package com.setaccio.lab.evidence;

import java.util.List;

public record EvidenceVerification(
        List<String> failures
) {

    public EvidenceVerification {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public boolean valid() {
        return failures.isEmpty();
    }
}
