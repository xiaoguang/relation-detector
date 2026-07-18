package com.relationdetector.contracts.spi;

import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * CN: 承载一次有界 live profiling 请求的状态、证据和脱敏 warnings。
 * EN: Carries status, evidence, and sanitized warnings from one bounded live profiling request.
 */
public record ProfileOutcome(
        ProfileStatus status,
        List<Evidence> evidence,
        List<WarningMessage> warnings
) {
    public ProfileOutcome {
        if (status == null) {
            throw new IllegalArgumentException("profile status is required");
        }
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static ProfileOutcome success(List<Evidence> evidence) {
        List<Evidence> items = List.copyOf(evidence == null ? List.of() : evidence);
        return new ProfileOutcome(items.isEmpty() ? ProfileStatus.NO_EVIDENCE : ProfileStatus.SUCCESS,
                items, List.of());
    }
}
