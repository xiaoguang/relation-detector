package com.relationdetector.postgres.fullgrammar.common;

/**
 * PostgreSQL DDL version policy for full-grammar event collection.
 *
 * <p>CN: 只承载跨版本 DDL 语义差异。当前唯一差异是 PostgreSQL 18 temporal
 * constraint 中的 PERIOD/WITHOUT column element 不应被当作普通 FK/index 列。
 *
 * <p>EN: Carries DDL semantic differences between PostgreSQL major versions.
 * The current difference is that PostgreSQL 18 temporal PERIOD/WITHOUT column
 * elements must not be emitted as ordinary FK/index columns.
 */
public record PostgresDdlVersionPolicy(boolean skipTemporalColumnElements) {
    public static PostgresDdlVersionPolicy standard() {
        return new PostgresDdlVersionPolicy(false);
    }

    public static PostgresDdlVersionPolicy temporalAware() {
        return new PostgresDdlVersionPolicy(true);
    }
}
