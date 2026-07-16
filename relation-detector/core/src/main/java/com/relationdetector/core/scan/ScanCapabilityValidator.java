package com.relationdetector.core.scan;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.spi.DatabaseAdaptor;

/**
 *
 * Validates requested scan sources before any live database connection is opened.
 */
final class ScanCapabilityValidator {
    void validate(ResolvedScanConfig config, DatabaseAdaptor adaptor) {
        boolean live = hasText(config.database().jdbcUrl());
        SourceConfig sources = config.sources();

        if (live && sources.metadataEnabled()) {
            require(adaptor, AdaptorCapability.METADATA, adaptor.collectors().metadata().isPresent(), "live metadata");
        }
        if (hasDdlFiles(sources)) {
            require(adaptor, AdaptorCapability.DDL_PARSING,
                    adaptor.parsers().structuredDdl().isPresent(), "DDL files");
        }
        if (live && sources.ddlEnabled() && sources.ddlFromDatabase()) {
            require(adaptor, AdaptorCapability.DDL_PARSING,
                    adaptor.collectors().databaseDdl().isPresent(), "live database DDL");
        }
        if (hasObjectFiles(sources) && adaptor.parsers().structuredSql().isEmpty()) {
            throw unsupported(adaptor, "object files", "structured SQL parser");
        }
        if (live && sources.objectsEnabled() && sources.objectsFromDatabase()) {
            require(adaptor, AdaptorCapability.DATABASE_OBJECTS,
                    adaptor.collectors().objects().isPresent(), "live database objects");
        }
        if (hasLogFiles(sources)) {
            require(adaptor, AdaptorCapability.NATIVE_LOGS,
                    adaptor.collectors().logs().isPresent(), "native log files");
        }
        if (live && config.evidence().dataProfileEnabled()) {
            require(adaptor, AdaptorCapability.DATA_PROFILING,
                    adaptor.profiling().dataProfiler().isPresent(), "live data profiling");
        }
    }

    private void require(
            DatabaseAdaptor adaptor,
            AdaptorCapability capability,
            boolean implementationPresent,
            String requestedSource
    ) {
        if (!adaptor.capabilities().contains(capability) || !implementationPresent) {
            throw unsupported(adaptor, requestedSource, capability.name());
        }
    }

    private IllegalArgumentException unsupported(DatabaseAdaptor adaptor, String source, String requirement) {
        return new IllegalArgumentException("adaptor=" + adaptor.id()
                + " requestedSource=" + source
                + " required=" + requirement);
    }

    private boolean hasDdlFiles(SourceConfig sources) {
        return sources.ddlEnabled()
                && (!sources.ddlFiles().isEmpty() || !sources.ddlPaths().isEmpty());
    }

    private boolean hasObjectFiles(SourceConfig sources) {
        return sources.objectsEnabled()
                && (!sources.objectFiles().isEmpty() || !sources.objectPaths().isEmpty());
    }

    private boolean hasLogFiles(SourceConfig sources) {
        return sources.logsEnabled()
                && (!sources.logFiles().isEmpty() || !sources.logPaths().isEmpty());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
