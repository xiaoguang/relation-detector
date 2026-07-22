package com.relationdetector.contracts.spi;

import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.WarningMessage;

/**
 * CN: 承载外部 adaptor 对一次有界 live profiling 请求返回的不可信 status/evidence/warning
 * envelope。core 只校验 failure warning 的 type/code，丢弃 plugin 的
 * message/source/attributes，并按已验证状态重建固定脱敏 warning。
 * EN: Carries the untrusted status/evidence/warning envelope returned by an external adaptor
 * for one bounded live profiling request. Core validates only failure-warning type/code,
 * discards plugin message/source/attributes, and rebuilds a fixed sanitized warning from the
 * validated status.
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
