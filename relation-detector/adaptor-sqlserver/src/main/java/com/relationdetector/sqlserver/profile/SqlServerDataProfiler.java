package com.relationdetector.sqlserver.profile;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/** Conservative bounded SQL Server data profiler. */
public final class SqlServerDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(new SqlServerProfileQueryRenderer());

    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        return delegate.profile(connection, request);
    }

    private static final class SqlServerProfileQueryRenderer implements DialectDataProfileQueryRenderer {
        private static final IdentifierQuoter QUOTER = IdentifierQuoter.sqlServer();

        @Override
        public String sourceName() {
            return "sqlserver-data-profile";
        }

        @Override
        public String render(ProfileRequest request) {
            return """
                SELECT COUNT(*) AS source_distinct,
                       ISNULL(SUM(CASE WHEN EXISTS (
                           SELECT 1 FROM %s t WHERE t.%s = s.v
                       ) THEN 1 ELSE 0 END), 0) AS matched_distinct
                FROM (
                    SELECT DISTINCT TOP (%d) %s AS v
                    FROM %s
                    WHERE %s IS NOT NULL
                ) s
                """.formatted(
                    QUOTER.table(request.candidate().target().table()),
                    QUOTER.column(request.candidate().target().column()),
                    Math.min(request.options().maxDistinctValues(), request.options().sampleRows()),
                    QUOTER.column(request.candidate().source().column()),
                    QUOTER.table(request.candidate().source().table()),
                    QUOTER.column(request.candidate().source().column()));
        }
    }
}
