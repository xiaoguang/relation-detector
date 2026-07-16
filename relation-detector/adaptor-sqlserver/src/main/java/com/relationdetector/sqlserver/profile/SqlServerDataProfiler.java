package com.relationdetector.sqlserver.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/**
 *
 * Exact SQL Server data profiler.
 */
public final class SqlServerDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(
            new SqlServerProfileQueryRenderer(), 229, 916);

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
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
                SELECT (SELECT COUNT_BIG(*) FROM %1$s s WHERE s.%2$s IS NOT NULL) AS source_non_null_rows,
                       (SELECT COUNT_BIG(DISTINCT s.%2$s) FROM %1$s s WHERE s.%2$s IS NOT NULL) AS source_distinct,
                       (SELECT COUNT_BIG(DISTINCT s.%2$s) FROM %1$s s
                          WHERE s.%2$s IS NOT NULL
                            AND EXISTS (SELECT 1 FROM %3$s t WHERE t.%4$s = s.%2$s)) AS matched_distinct,
                       (SELECT COUNT_BIG(DISTINCT t.%4$s) FROM %3$s t WHERE t.%4$s IS NOT NULL) AS target_distinct
                """.formatted(
                    QUOTER.table(request.candidate().source().table()),
                    QUOTER.column(request.candidate().source().column()),
                    QUOTER.table(request.candidate().target().table()),
                    QUOTER.column(request.candidate().target().column()));
        }
    }
}
