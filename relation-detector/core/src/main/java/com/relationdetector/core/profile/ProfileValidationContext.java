package com.relationdetector.core.profile;

import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.adaptor.AdaptorContractException;

/**
 * CN: 保存插件调用前由 core 独立捕获的 profile request 和负向 evidence 资格；不引用传给插件的 request。
 * EN: Holds the core-owned profile request snapshot and negative-evidence eligibility captured before the plugin call.
 */
public record ProfileValidationContext(
        ProfileRequest request,
        ProfileOutcomeContractValidator.NegativeProfileEligibility negativeEligibility
) {
    public ProfileValidationContext {
        if (request == null || negativeEligibility == null) {
            throw new AdaptorContractException("Data profiler violated the ProfileOutcome contract");
        }
    }
}
