package com.relationdetector.api;

/**
 * DDL text collected from the live database catalog.
 *
 * <p>Design mapping: this is the database counterpart of user-provided DDL
 * files. For MySQL the first producer is {@code SHOW CREATE TABLE}; future
 * adaptors can map PostgreSQL/SQL Server/Oracle catalog DDL reconstruction into
 * the same record. The scanner feeds {@link #ddl()} into the normal DDL parser
 * runner, then rewrites evidence provenance to {@code DATABASE_DDL} so operators
 * can distinguish catalog-derived DDL from checked-in schema files.
 */
public record DatabaseDdlDefinition(
        String schema,
        String name,
        String ddl,
        String source
) {
}
