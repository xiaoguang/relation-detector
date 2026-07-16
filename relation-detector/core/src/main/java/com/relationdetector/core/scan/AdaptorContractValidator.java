package com.relationdetector.core.scan;

import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.spi.DatabaseAdaptor;

/** Validates adaptor identity and SPI compatibility before a scan opens JDBC. */
public final class AdaptorContractValidator {
    public void validate(DatabaseConfig database, DatabaseAdaptor adaptor) {
        validateSpiVersion(adaptor);
        if (!adaptor.supportedDatabaseTypes().contains(database.databaseType())) {
            throw new IllegalArgumentException("adaptor=" + adaptor.id()
                    + " does not support database type " + database.databaseType());
        }
        if (hasText(database.adaptorId()) && !database.adaptorId().equals(adaptor.id())) {
            throw new IllegalArgumentException("configured adaptor id=" + database.adaptorId()
                    + " does not match adaptor id=" + adaptor.id());
        }
    }

    public void validateSpiVersion(DatabaseAdaptor adaptor) {
        if (adaptor == null) {
            throw new IllegalArgumentException("database adaptor is required");
        }
        int actual = adaptor.spiVersion();
        if (actual != AdaptorApiVersion.CURRENT) {
            throw new IllegalArgumentException("adaptor SPI version mismatch: plugin=" + adaptor.id()
                    + ", actual=" + actual + ", required=" + AdaptorApiVersion.CURRENT
                    + "; recompile the plugin against the current relation-detector contracts");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
