package com.relationdetector.postgres.common;

import java.util.List;

/**
 * CN: 表示 PostgreSQL UNION/UNION ALL 各 branch 的 canonical output ordinal 布局；visitor 提供 typed projection names，结果告诉 lineage 是否可安全按位置合并，不解析 SQL 文本。
 * EN: Represents the canonical output-ordinal layout for PostgreSQL UNION branches. Typed visitors provide projection names; the result tells lineage whether positional merging is safe without parsing SQL text.
 */
public record PostgresSetProjectionLayout(List<String> columns, boolean arityMatches) {
    public PostgresSetProjectionLayout {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public static PostgresSetProjectionLayout resolve(
            List<String> explicitColumns,
            List<String> firstBranchColumns,
            List<Integer> branchArities
    ) {
        List<String> explicit = explicitColumns == null ? List.of() : List.copyOf(explicitColumns);
        List<String> first = firstBranchColumns == null ? List.of() : List.copyOf(firstBranchColumns);
        boolean firstNamesKnown = !first.isEmpty() && first.stream().noneMatch(String::isBlank);
        List<String> columns = !explicit.isEmpty() ? explicit : firstNamesKnown ? first : List.of();
        int expectedArity = !columns.isEmpty() ? columns.size() : firstKnownArity(branchArities);
        boolean matches = branchArities != null && (expectedArity < 0
                || branchArities.stream().allMatch(arity -> arity < 0 || arity == expectedArity));
        return new PostgresSetProjectionLayout(columns, matches);
    }

    private static int firstKnownArity(List<Integer> branchArities) {
        if (branchArities == null) {
            return -1;
        }
        return branchArities.stream().filter(arity -> arity >= 0).findFirst().orElse(-1);
    }

    public static int branchArity(int itemCount, boolean containsWildcard) {
        return containsWildcard ? -1 : itemCount;
    }

    public static final class Cursor {
        private final List<String> columns;
        private int index;

        public Cursor(List<String> columns) {
            this.columns = List.copyOf(columns);
        }

        public void reset() {
            index = 0;
        }

        public String nextOr(String fallback) {
            return index < columns.size() ? columns.get(index++) : fallback;
        }
    }
}
