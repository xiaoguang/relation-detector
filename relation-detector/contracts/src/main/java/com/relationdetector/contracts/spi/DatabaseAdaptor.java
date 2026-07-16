package com.relationdetector.contracts.spi;

import java.util.Set;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * Java SPI 加载的数据库 adaptor 公共契约。
 *
 * <p>CN: adaptor 负责数据库特有采集和 parser 入口；core 仍负责最终 relationship
 * 合并、lineage 合并、confidence 和输出。MySQL、PostgreSQL、Oracle 和 SQL Server
 * 均通过同一 grouped capability 契约提供可用的 live/file 实现。
 *
 * <p>EN: Public database adaptor contract loaded through Java SPI. Adaptors own
 * database-specific collection and parser entry points; core owns final merging,
 * confidence, lineage merging, and output. MySQL, PostgreSQL, Oracle, and SQL
 * Server expose their live and file implementations through the same grouped
 * capability contract.
 */
public interface DatabaseAdaptor {
    /**
     * Binary SPI version. Implementations that do not explicitly implement the
     * current grouped parser contract, including dialect script framing, inherit
     * {@code 1} and are rejected before capabilities are used.
     */
    default int spiVersion() {
        return 1;
    }

    String id();

    String displayName();

    Set<DatabaseType> supportedDatabaseTypes();

    Set<AdaptorCapability> capabilities();

    IdentifierRules identifierRules();

    /**
     * Maps user-facing catalog/schema scope onto the dialect's canonical
     * namespace axes before any live collector or parser receives it.
     */
    default ScanScope canonicalizeScope(ScanScope scope) {
        return scope;
    }

    /**
     * CN: 返回仅属于当前 JDBC 方言的权限拒绝 vendor code；共享诊断层不会隐式应用其他数据库的 code。
     *
     * <p>EN: Returns permission-denied vendor codes owned by this JDBC dialect; shared diagnostics never apply
     * another database's codes implicitly.
     */
    default Set<Integer> permissionDeniedVendorCodes() {
        return Set.of();
    }

    AdaptorCollectors collectors();

    AdaptorParsers parsers();

    AdaptorProfiling profiling();
}
