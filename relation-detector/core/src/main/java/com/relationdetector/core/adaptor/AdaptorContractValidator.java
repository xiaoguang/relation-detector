package com.relationdetector.core.adaptor;

import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.config.DatabaseConfig;

/**
 * CN: 在 JDBC 打开前验证 adaptor id、database type 与 SPI v6 二进制契约，旧插件不进入 scan。
 * EN: Validates adaptor id, database type, and the SPI v6 binary contract before JDBC is opened.
 */
public final class AdaptorContractValidator {
    public DatabaseAdaptor validate(DatabaseConfig database, DatabaseAdaptor adaptor) {
        DatabaseAdaptor validated = validateSpiVersion(adaptor);
        if (!validated.supportedDatabaseTypes().contains(database.databaseType())) {
            throw new AdaptorContractException("adaptor=" + validated.id()
                    + " does not support database type " + database.databaseType());
        }
        if (hasText(database.adaptorId()) && !database.adaptorId().equals(validated.id())) {
            throw new AdaptorContractException("configured adaptor id=" + database.adaptorId()
                    + " does not match adaptor id=" + validated.id());
        }
        return validated;
    }

    public DatabaseAdaptor validateSpiVersion(DatabaseAdaptor adaptor) {
        return ValidatedDatabaseAdaptor.snapshot(adaptor, AdaptorApiVersion.CURRENT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
