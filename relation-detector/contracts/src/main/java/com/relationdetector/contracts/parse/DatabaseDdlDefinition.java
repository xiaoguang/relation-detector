package com.relationdetector.contracts.parse;

/**
 * 从 live database catalog 采集到的 DDL 文本。
 *
 * <p>CN: 它是用户提供 DDL 文件的数据库侧对应物。ScanEngine 会把 ddl() 送入正常
 * DDL parser runner，然后把 evidence provenance 改写为 DATABASE_DDL。
 *
 * <p>EN: DDL text collected from the live database catalog. It is the database
 * counterpart of user-provided DDL files. ScanEngine feeds ddl() into the normal
 * DDL parser runner and rewrites evidence provenance to DATABASE_DDL.
 */
public record DatabaseDdlDefinition(
        String catalog,
        String schema,
        String name,
        String ddl,
        String source
) {
}
