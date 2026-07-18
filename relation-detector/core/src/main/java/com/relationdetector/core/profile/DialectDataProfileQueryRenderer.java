package com.relationdetector.core.profile;

import com.relationdetector.contracts.spi.ProfileRequest;

/**
 * CN: 为已通过 namespace 策略的 candidate 渲染一条方言专属的有界 containment query。
 * EN: Renders one dialect-specific bounded containment query for a candidate that has passed namespace policy.
 */
public interface DialectDataProfileQueryRenderer {
    String sourceName();

    String render(ProfileRequest request);
}
