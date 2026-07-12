package com.relationdetector.postgres.common;

import java.util.List;

/** Canonical ordinal layout shared by typed PostgreSQL set-operation visitors. */
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
