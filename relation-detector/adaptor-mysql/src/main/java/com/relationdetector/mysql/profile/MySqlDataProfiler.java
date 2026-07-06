package com.relationdetector.mysql.profile;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/** Bounded MySQL data profiler. */
public final class MySqlDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(new MySqlProfileQueryRenderer());

    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        return delegate.profile(connection, request);
    }

    private static final class MySqlProfileQueryRenderer implements DialectDataProfileQueryRenderer {
        private static final IdentifierQuoter QUOTER = IdentifierQuoter.mysql();

        @Override
        public String sourceName() {
            return "mysql-data-profile";
        }

        @Override
        public String render(ProfileRequest request) {
            return """
                SELECT COUNT(*) AS source_distinct,
                       COALESCE(SUM(CASE WHEN EXISTS (
                           SELECT 1 FROM %s t WHERE t.%s = s.v
                       ) THEN 1 ELSE 0 END), 0) AS matched_distinct
                FROM (
                    SELECT DISTINCT %s AS v
                    FROM %s
                    WHERE %s IS NOT NULL
                    LIMIT %d
                ) s
                """.formatted(
                    QUOTER.table(request.candidate().target().table().displayName()),
                    QUOTER.column(request.candidate().target().column().columnName()),
                    QUOTER.column(request.candidate().source().column().columnName()),
                    QUOTER.table(request.candidate().source().table().displayName()),
                    QUOTER.column(request.candidate().source().column().columnName()),
                    Math.min(request.options().maxDistinctValues(), request.options().sampleRows()));
        }
    }
}
