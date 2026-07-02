package com.relationdetector.oracle.profile;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileRequest;

/**
 * Conservative Oracle data profiler placeholder.
 */
public final class OracleDataProfiler implements DataProfiler {
    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        return List.of();
    }
}
