package com.relationdetector.core.profile;

import com.relationdetector.contracts.spi.ProfileRequest;

/**
 * Renders one bounded containment profiling query for a SQL dialect.
 */
public interface DialectDataProfileQueryRenderer {
    String sourceName();

    String render(ProfileRequest request);
}
