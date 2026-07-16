package com.relationdetector.mysql.profile;

import java.sql.Connection;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.core.profile.DialectDataProfileQueryRenderer;
import com.relationdetector.core.profile.IdentifierQuoter;
import com.relationdetector.core.profile.JdbcDataProfilerTemplate;

/**
 * CN: 使用只读聚合查询测量 MySQL 候选键的非空行、distinct 与 containment；不创建或定向 relationship。
 * EN: Measures non-null rows, distinct values, and containment for MySQL candidates with read-only aggregate SQL;
 * it does not create or orient relationships.
 */
public final class MySqlDataProfiler implements DataProfiler {
    private final JdbcDataProfilerTemplate delegate = new JdbcDataProfilerTemplate(new MySqlProfileQueryRenderer());

    @Override
    public ProfileOutcome profile(Connection connection, ProfileRequest request) {
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
