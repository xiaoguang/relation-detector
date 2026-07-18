package com.relationdetector.oracle.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/**
 * CN: 对已验证且 owner-compatible 的 Oracle physical-column candidate 执行 bounded live metrics query，并交给公共 builder 生成 profile evidence；不发现候选或渲染 catalog。
 * EN: Executes bounded live metric queries for validated owner-compatible Oracle physical-column candidates and delegates evidence construction to the shared builder. It neither discovers candidates nor renders catalog qualifiers.
 */
public final class OracleDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(
            new OracleProfileQueryRenderer(), 1031);

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
        return delegate.profile(connection, request);
    }

    private static final class OracleProfileQueryRenderer implements DialectDataProfileQueryRenderer {
        private static final IdentifierQuoter QUOTER = IdentifierQuoter.oracle();

        @Override
        public String sourceName() {
            return "oracle-data-profile";
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
                FROM DUAL
                """.formatted(
                    QUOTER.table(request.candidate().source().table()),
                    QUOTER.column(request.candidate().source().column()),
                    QUOTER.table(request.candidate().target().table()),
                    QUOTER.column(request.candidate().target().column()));
        }
    }
}
