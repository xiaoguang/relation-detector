package com.relationdetector.contracts.scoring;

/**
 * 默认 evidence 分值常量。
 *
 * <p>CN: 设计真源是 {@code docs/design/phase-02-core-model-scoring.md} 的
 * “置信度计算”。parser、metadata enhancer 和 adaptor 共享这些基础分，避免各自
 * 悄悄漂移。
 *
 * <p>EN: Default evidence weights shared by core parsers and database adaptors.
 * The design source of truth is the confidence section in
 * docs/design/phase-02-core-model-scoring.md. Centralizing these constants keeps
 * parser/adaptor implementations aligned.
 */
public final class DefaultEvidenceScores {
    private DefaultEvidenceScores() {
    }

    public static final double METADATA_FOREIGN_KEY = 0.98d;
    public static final double DDL_FOREIGN_KEY = 0.90d;
    public static final double VIEW_JOIN = 0.72d;
    public static final double PROCEDURE_JOIN = 0.70d;
    public static final double TRIGGER_REFERENCE = 0.65d;
    public static final double SQL_LOG_JOIN = 0.55d;
    public static final double SQL_LOG_SUBQUERY_IN = 0.58d;
    public static final double SQL_LOG_EXISTS = 0.58d;
    public static final double SQL_LOG_COLUMN_CO_OCCURRENCE = 0.40d;
    public static final double SQL_LOG_TABLE_CO_OCCURRENCE = 0.25d;
    public static final double NAMING_MATCH = 0.20d;
    public static final double SOURCE_INDEX = 0.10d;
    public static final double TARGET_UNIQUE = 0.18d;
    public static final double COLUMN_TYPE_COMPATIBLE = 0.08d;
    public static final double VALUE_CONTAINMENT_HIGH = 0.30d;
    public static final double VALUE_OVERLAP_HIGH = 0.20d;
    public static final double NEGATIVE_VALUE_MISMATCH = -0.30d;
    public static final double REPEATED_OBSERVATION_MAX = 0.10d;
}
