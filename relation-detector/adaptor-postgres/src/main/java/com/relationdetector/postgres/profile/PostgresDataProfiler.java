package com.relationdetector.postgres.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/**
 * CN: 为已验证的 PostgreSQL physical column candidate 渲染 bounded live SQL，并把独立 row/distinct/match metrics 交给公共 evidence builder；不发现候选或执行离线采样。
 * EN: Renders bounded live SQL for validated PostgreSQL physical-column candidates and delegates independent row, distinct, and match metrics to the shared evidence builder. It neither discovers candidates nor samples offline data.
 */
public final class PostgresDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(new PostgresProfileQueryRenderer());

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
        return delegate.profile(connection, request);
    }

    private static final class PostgresProfileQueryRenderer implements DialectDataProfileQueryRenderer {
        private static final IdentifierQuoter QUOTER = IdentifierQuoter.postgres();

        @Override
        public String sourceName() {
            return "postgres-data-profile";
        }

        @Override
        public String render(ProfileRequest request) {
            return """
                SELECT (SELECT COUNT(*) FROM %1$s s WHERE s.%2$s IS NOT NULL) AS source_non_null_rows,
                       (SELECT COUNT(DISTINCT s.%2$s) FROM %1$s s WHERE s.%2$s IS NOT NULL) AS source_distinct,
                       (SELECT COUNT(DISTINCT s.%2$s) FROM %1$s s
                          WHERE s.%2$s IS NOT NULL
                            AND EXISTS (SELECT 1 FROM %3$s t WHERE t.%4$s = s.%2$s)) AS matched_distinct,
                       (SELECT COUNT(DISTINCT t.%4$s) FROM %3$s t WHERE t.%4$s IS NOT NULL) AS target_distinct
                """.formatted(
                    QUOTER.table(request.candidate().source().table()),
                    QUOTER.column(request.candidate().source().column()),
                    QUOTER.table(request.candidate().target().table()),
                    QUOTER.column(request.candidate().target().column()));
        }
    }
}
