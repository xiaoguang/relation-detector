package com.relationdetector.sqlserver.profile;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileRequest;

/** Conservative SQL Server data profiler placeholder. */
public final class SqlServerDataProfiler implements DataProfiler {
    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        return List.of();
    }
}
