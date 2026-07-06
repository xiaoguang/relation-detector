package com.relationdetector.core.profile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileRequest;

/**
 * Shared bounded JDBC profiler. Dialect classes only render SQL.
 */
public final class JdbcDataProfilerTemplate implements DataProfiler {
    private final DialectDataProfileQueryRenderer renderer;
    private final DataProfileEvidenceBuilder evidenceBuilder = new DataProfileEvidenceBuilder();

    public JdbcDataProfilerTemplate(DialectDataProfileQueryRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        if (connection == null || request == null || !request.candidate().source().isColumnLevel()
                || !request.candidate().target().isColumnLevel()) {
            return List.of();
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(request.options().timeoutSeconds());
            try (ResultSet rs = statement.executeQuery(renderer.render(request))) {
                if (rs.next()) {
                    return evidenceBuilder.build(request, metrics(rs), renderer.sourceName());
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private DataProfileMetrics metrics(ResultSet rs) throws java.sql.SQLException {
        long sourceDistinct = rs.getLong("source_distinct");
        long matched = rs.getLong("matched_distinct");
        long missing = Math.max(0, sourceDistinct - matched);
        return DataProfileMetrics.live(sourceDistinct, sourceDistinct, matched, missing, sourceDistinct, false, false);
    }
}
