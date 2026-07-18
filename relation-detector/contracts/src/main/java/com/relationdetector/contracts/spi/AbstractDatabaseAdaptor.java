package com.relationdetector.contracts.spi;

import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * CN: 为内建 adaptor 固定组装不可变的 collector、parser、profiling 与 identifier 能力，不实现方言语义。
 * EN: Assembles immutable collector, parser, profiling, and identifier capabilities for built-in adaptors without implementing dialect semantics.
 *
 * <p>SPI v6 exposes grouped optional collectors, typed parser capabilities,
 * profile outcomes, and a required dialect script framer. This base class
 * keeps the immutable assembly shared by built-in adaptors.
 */
public abstract class AbstractDatabaseAdaptor implements DatabaseAdaptor {
    private final String id;
    private final String displayName;
    private final Set<DatabaseType> supportedDatabaseTypes;
    private final Set<AdaptorCapability> capabilities;
    private final IdentifierRules identifierRules;
    private final AdaptorCollectors collectors;
    private final AdaptorParsers parsers;
    private final AdaptorProfiling profiling;

    protected AbstractDatabaseAdaptor(
            String id,
            String displayName,
            Set<DatabaseType> supportedDatabaseTypes,
            Set<AdaptorCapability> capabilities,
            IdentifierRules identifierRules,
            AdaptorCollectors collectors,
            AdaptorParsers parsers,
            AdaptorProfiling profiling
    ) {
        this.id = id;
        this.displayName = displayName;
        this.supportedDatabaseTypes = Set.copyOf(supportedDatabaseTypes);
        this.capabilities = Set.copyOf(capabilities);
        this.identifierRules = identifierRules;
        this.collectors = collectors;
        this.parsers = parsers;
        this.profiling = profiling;
    }

    @Override
    public final int spiVersion() {
        return AdaptorApiVersion.CURRENT;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final Set<DatabaseType> supportedDatabaseTypes() {
        return supportedDatabaseTypes;
    }

    @Override
    public final Set<AdaptorCapability> capabilities() {
        return capabilities;
    }

    @Override
    public final IdentifierRules identifierRules() {
        return identifierRules;
    }

    @Override
    public final AdaptorCollectors collectors() {
        return collectors;
    }

    @Override
    public final AdaptorParsers parsers() {
        return parsers;
    }

    @Override
    public final AdaptorProfiling profiling() {
        return profiling;
    }
}
