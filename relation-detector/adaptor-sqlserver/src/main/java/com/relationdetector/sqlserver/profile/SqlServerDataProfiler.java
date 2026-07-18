package com.relationdetector.sqlserver.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/**
 * CN: 对 catalog/schema 已验证的 SQL Server physical-column candidate 执行 bounded metrics SQL，并将结果交给公共 evidence builder；不发现候选或解释 driver message。
 * EN: Executes bounded metric SQL for catalog/schema-validated SQL Server physical-column candidates and delegates results to the shared evidence builder. It neither discovers candidates nor exposes driver messages.
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
