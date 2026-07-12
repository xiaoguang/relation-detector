package com.relationdetector.contracts.spi;

import java.util.Set;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * Java SPI 加载的数据库 adaptor 公共契约。
 *
 * <p>CN: adaptor 负责数据库特有采集和 parser 入口；core 仍负责最终 relationship
 * 合并、lineage 合并、confidence 和输出。接口保留 SQL Server/Oracle 扩展空间。
 *
 * <p>EN: Public database adaptor contract loaded through Java SPI. Adaptors own
 * database-specific collection and parser entry points; core owns final merging,
 * confidence, lineage merging, and output. The contract leaves room for future
 * SQL Server/Oracle adaptors.
 */
public interface DatabaseAdaptor {
    /**
     * Binary SPI version. Implementations that do not explicitly implement the
     * current grouped parser contract, including dialect script parsing, inherit
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

    AdaptorCollectors collectors();

    AdaptorParsers parsers();

    AdaptorProfiling profiling();
}
