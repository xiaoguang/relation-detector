package com.relationdetector.postgres.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/** Bounded PostgreSQL data profiler. */
public final class PostgresDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(new PostgresProfileQueryRenderer());

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
        return delegate.profile(connection, request);
    }

    private static final class PostgresProfileQueryRenderer implements DialectDataProfileQueryRenderer {
        private static final IdentifierQuoter QUOTER = IdentifierQuoter.doubleQuote();

        @Override
        public String sourceName() {
            return "postgres-data-profile";
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
                    QUOTER.table(request.candidate().target().table()),
                    QUOTER.column(request.candidate().target().column()),
                    QUOTER.column(request.candidate().source().column()),
                    QUOTER.table(request.candidate().source().table()),
                    QUOTER.column(request.candidate().source().column()),
                    Math.min(request.options().maxDistinctValues(), request.options().sampleRows()));
        }
    }
}
