package com.relationdetector.api;

/**
 * Central enum definitions for the public adaptor contract.
 *
 * <p>Design mapping:
 * docs/design/enum-reference.md is the source of truth for the meaning,
 * JSON spelling, and maintenance rules of these values. Keeping the enums in
 * one file makes it harder for adaptor authors to invent near-duplicate states.
 */
public final class Enums {
    private Enums() {
    }

    /** Database selected by YAML database.type and used for adaptor matching. */
    public enum DatabaseType {
        MYSQL,
        POSTGRESQL,
        SQLSERVER,
        ORACLE
    }

    /** User-facing output format selected by --format or output.format. */
    public enum OutputFormat {
        JSON,
        TABLE
    }

    /** Coarse final relationship class. */
    public enum RelationType {
        /** A source column probably references a target primary/unique column. */
        FK_LIKE,
        /** Two tables co-occur but no reliable column-level reference is known. */
        CO_OCCURRENCE
    }

    /** Main trust shape of a relationship after all evidence is merged. */
    public enum RelationSubType {
        DECLARED_FK,
        DDL_DECLARED_FK,
        INFERRED_JOIN_FK,
        SUBQUERY_INFERRED_FK,
        PROFILE_SUPPORTED_FK,
        NAMING_SUPPORTED_FK,
        COLUMN_CO_OCCURRENCE,
        TABLE_CO_OCCURRENCE
    }

    /** Field-level value flow category for Data Lineage output. */
    public enum LineageFlowKind {
        VALUE,
        CONTROL
    }

    /** Coarse transform shape used to score and explain Data Lineage. */
    public enum LineageTransformType {
        DIRECT,
        AGGREGATE,
        COALESCE,
        CASE_WHEN,
        CONCAT_FORMAT,
        ARITHMETIC,
        FUNCTION_CALL,
        WINDOW_DERIVED,
        UNKNOWN_EXPRESSION
    }

    /** Individual evidence type used by the scoring model. */
    public enum EvidenceType {
        METADATA_FOREIGN_KEY,
        DDL_FOREIGN_KEY,
        VIEW_JOIN,
        PROCEDURE_JOIN,
        TRIGGER_REFERENCE,
        SQL_LOG_JOIN,
        SQL_LOG_SUBQUERY_IN,
        SQL_LOG_EXISTS,
        SQL_LOG_COLUMN_CO_OCCURRENCE,
        SQL_LOG_TABLE_CO_OCCURRENCE,
        NAMING_MATCH,
        SOURCE_INDEX,
        TARGET_UNIQUE,
        COLUMN_TYPE_COMPATIBLE,
        VALUE_CONTAINMENT_HIGH,
        VALUE_OVERLAP_HIGH,
        NEGATIVE_VALUE_MISMATCH,
        REPEATED_OBSERVATION
    }

    /** Where an evidence item came from; not the same as what the evidence says. */
    public enum EvidenceSourceType {
        METADATA,
        DDL_FILE,
        DATABASE_DDL,
        DATABASE_OBJECT,
        NATIVE_LOG,
        PLAIN_SQL,
        DATA_PROFILE,
        NAMING_HEURISTIC
    }

    /** Source category for a SQL statement entering the parser. */
    public enum StatementSourceType {
        DDL_FILE,
        PROCEDURE,
        FUNCTION,
        VIEW,
        MATERIALIZED_VIEW,
        TRIGGER,
        EVENT,
        RULE,
        PACKAGE,
        PACKAGE_BODY,
        MIGRATION,
        NATIVE_LOG,
        PLAIN_SQL
    }

    /** Persistent database object kinds that may contain relationship SQL. */
    public enum DatabaseObjectType {
        PROCEDURE,
        FUNCTION,
        VIEW,
        MATERIALIZED_VIEW,
        TRIGGER,
        EVENT,
        RULE,
        PACKAGE,
        PACKAGE_BODY
    }

    /** Structured parser event categories emitted before relationship scoring. */
    public enum StructuredParseEventType {
        TABLE_REFERENCE,
        COLUMN_EQUALITY,
        ROWSET_REFERENCE,
        PREDICATE_EQUALITY,
        JOIN_USING_COLUMNS,
        EXISTS_PREDICATE,
        IN_SUBQUERY_PREDICATE,
        TUPLE_IN_SUBQUERY_PREDICATE,
        CTE_DECLARATION,
        IGNORED_ROWSET,
        LOCAL_TEMP_TABLE_DECLARATION,
        TRIGGER_TARGET_TABLE,
        TRIGGER_PSEUDO_ROWSET,
        WRITE_TARGET,
        UPDATE_ASSIGNMENT,
        INSERT_SELECT_MAPPING,
        MERGE_WRITE_MAPPING,
        PROJECTION_ITEM,
        EXPRESSION_SOURCE,
        DDL_FOREIGN_KEY,
        DDL_INDEX,
        DYNAMIC_SQL
    }

    /** Hint used by adaptors when extracting SQL from raw log files. */
    public enum LogFormatHint {
        AUTO,
        PLAIN_SQL,
        MYSQL_GENERAL_LOG,
        MYSQL_SLOW_LOG,
        POSTGRES_STATEMENT_LOG
    }

    /** Parser confidence about source -> target direction before final scoring. */
    public enum DirectionConfidence {
        CERTAIN,
        HIGH,
        MEDIUM,
        LOW,
        AMBIGUOUS
    }

    /** Non-fatal scan problem category. */
    public enum WarningType {
        CONFIG_WARNING,
        PERMISSION_WARNING,
        PARSE_WARNING,
        PROFILE_WARNING,
        AMBIGUOUS_RELATION_WARNING,
        ADAPTOR_CAPABILITY_WARNING
    }

    /** Severity of a warning. ERROR severity does not always mean non-zero exit. */
    public enum WarningSeverity {
        INFO,
        WARN,
        ERROR
    }

    /** Stable CLI process exit codes. */
    public enum ErrorCode {
        OK(0),
        CONFIG_FILE_ERROR(1),
        CONFIG_FORMAT_ERROR(2),
        ARGUMENT_ERROR(3),
        ADAPTOR_ERROR(4),
        INPUT_FILE_ERROR(5),
        DATABASE_CONNECTION_ERROR(10),
        SCAN_RUNTIME_ERROR(11),
        OUTPUT_WRITE_ERROR(12);

        private final int code;

        ErrorCode(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /** Capabilities exposed by each database adaptor for configuration checks. */
    public enum AdaptorCapability {
        METADATA,
        DDL_PARSING,
        DATABASE_OBJECTS,
        NATIVE_LOGS,
        DATA_PROFILING,
        EVIDENCE_WEIGHT_ADJUSTMENT
    }

    /** Enabled scan source in YAML sources.*. */
    public enum ScanSourceKind {
        METADATA,
        DDL,
        OBJECTS,
        LOGS,
        DATA_PROFILE
    }
}
