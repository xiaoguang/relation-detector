package com.relationdetector.core.scan;

import java.sql.Connection;
import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;

/**
 * CN: 在 JDBC 前冻结外部 adaptor 的声明式 SPI shape，向 capability preflight 和 scan
 * 提供不可变快照；输入是一次读取的插件声明，输出是稳定的 grouped capability 视图。它委托
 * 方言 scope 解析并校验返回值，但不执行 collector、parser 或 profiling 业务。
 *
 * <p>EN: Freezes an external adaptor's declarative SPI shape before JDBC is
 * opened and supplies an immutable view to capability preflight and scanning.
 * It delegates dialect scope resolution while validating returned scopes, but
 * does not execute collector, parser, or profiling behavior.
 */
final class ValidatedDatabaseAdaptor implements DatabaseAdaptor {
    private final DatabaseAdaptor delegate;
    private final int spiVersion;
    private final String id;
    private final String displayName;
    private final Set<DatabaseType> supportedDatabaseTypes;
    private final Set<AdaptorCapability> capabilities;
    private final IdentifierRules identifierRules;
    private final Set<Integer> permissionDeniedVendorCodes;
    private final AdaptorCollectors collectors;
    private final AdaptorParsers parsers;
    private final AdaptorProfiling profiling;

    private ValidatedDatabaseAdaptor(
            DatabaseAdaptor delegate,
            int spiVersion,
            String id,
            String displayName,
            Set<DatabaseType> supportedDatabaseTypes,
            Set<AdaptorCapability> capabilities,
            IdentifierRules identifierRules,
            Set<Integer> permissionDeniedVendorCodes,
            AdaptorCollectors collectors,
            AdaptorParsers parsers,
            AdaptorProfiling profiling
    ) {
        this.delegate = delegate;
        this.spiVersion = spiVersion;
        this.id = id;
        this.displayName = displayName;
        this.supportedDatabaseTypes = supportedDatabaseTypes;
        this.capabilities = capabilities;
        this.identifierRules = identifierRules;
        this.permissionDeniedVendorCodes = permissionDeniedVendorCodes;
        this.collectors = collectors;
        this.parsers = parsers;
        this.profiling = profiling;
    }

    static DatabaseAdaptor snapshot(DatabaseAdaptor adaptor, int requiredSpiVersion) {
        if (adaptor instanceof ValidatedDatabaseAdaptor) {
            return adaptor;
        }
        if (adaptor == null) {
            throw violation("database adaptor is required");
        }
        try {
            int spiVersion = adaptor.spiVersion();
            String id = requireText(adaptor.id(), "adaptor id");
            if (spiVersion != requiredSpiVersion) {
                throw violation("adaptor SPI version mismatch: plugin=" + id
                        + ", actual=" + spiVersion + ", required=" + requiredSpiVersion
                        + "; recompile the plugin against the current relation-detector contracts");
            }
            String displayName = requireText(adaptor.displayName(), "adaptor display name");
            Set<DatabaseType> supportedTypes = immutableNonEmptySet(
                    adaptor.supportedDatabaseTypes(), "supported database types");
            Set<AdaptorCapability> capabilities = immutableSet(
                    adaptor.capabilities(), "adaptor capabilities");
            IdentifierRules identifierRules = requireNonNull(
                    adaptor.identifierRules(), "identifier rules");
            Set<Integer> permissionCodes = immutableSet(
                    adaptor.permissionDeniedVendorCodes(), "permission vendor codes");
            AdaptorCollectors collectors = copyCollectors(adaptor.collectors());
            AdaptorParsers parsers = copyParsers(adaptor.parsers());
            AdaptorProfiling profiling = copyProfiling(adaptor.profiling());
            return new ValidatedDatabaseAdaptor(
                    adaptor,
                    spiVersion,
                    id,
                    displayName,
                    supportedTypes,
                    capabilities,
                    identifierRules,
                    permissionCodes,
                    collectors,
                    parsers,
                    profiling);
        } catch (AdaptorContractException error) {
            throw error;
        } catch (RuntimeException | LinkageError error) {
            throw violation("database adaptor returned an invalid SPI shape");
        }
    }

    private static AdaptorCollectors copyCollectors(AdaptorCollectors value) {
        AdaptorCollectors collectors = requireNonNull(value, "adaptor collectors");
        Optional<com.relationdetector.contracts.spi.Collectors.MetadataCollector> metadata =
                requireNonNull(collectors.metadata(), "metadata collector group");
        Optional<com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector> objects =
                requireNonNull(collectors.objects(), "object collector group");
        Optional<com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector> databaseDdl =
                requireNonNull(collectors.databaseDdl(), "database DDL collector group");
        Optional<com.relationdetector.contracts.spi.Collectors.SqlLogExtractor> logs =
                requireNonNull(collectors.logs(), "log collector group");
        return new AdaptorCollectors(metadata, objects, databaseDdl, logs);
    }

    private static AdaptorParsers copyParsers(AdaptorParsers value) {
        AdaptorParsers parsers = requireNonNull(value, "adaptor parsers");
        return new AdaptorParsers(
                requireNonNull(parsers.sqlRelations(), "SQL relationship parser"),
                requireNonNull(parsers.structuredSql(), "structured SQL parser group"),
                requireNonNull(parsers.structuredDdl(), "structured DDL parser group"),
                requireNonNull(parsers.scriptFramer(), "script framer"));
    }

    private static AdaptorProfiling copyProfiling(AdaptorProfiling value) {
        AdaptorProfiling profiling = requireNonNull(value, "adaptor profiling");
        return new AdaptorProfiling(
                requireNonNull(profiling.dataProfiler(), "data profiler group"),
                requireNonNull(profiling.evidenceWeightAdjuster(), "evidence weight adjuster"));
    }

    private static <T> Set<T> immutableSet(Set<T> values, String field) {
        requireNonNull(values, field);
        try {
            return Set.copyOf(values);
        } catch (RuntimeException error) {
            throw violation(field + " contains an invalid value");
        }
    }

    private static <T> Set<T> immutableNonEmptySet(Set<T> values, String field) {
        Set<T> copy = immutableSet(values, field);
        if (copy.isEmpty()) {
            throw violation(field + " must not be empty");
        }
        return copy;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw violation(field + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw violation(field + " must not be null");
        }
        return value;
    }

    private static AdaptorContractException violation(String message) {
        return new AdaptorContractException(message);
    }

    @Override
    public int spiVersion() {
        return spiVersion;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Set<DatabaseType> supportedDatabaseTypes() {
        return supportedDatabaseTypes;
    }

    @Override
    public Set<AdaptorCapability> capabilities() {
        return capabilities;
    }

    @Override
    public IdentifierRules identifierRules() {
        return identifierRules;
    }

    @Override
    public ScanScope canonicalizeScope(ScanScope scope) {
        ScanScope resolved = delegate.canonicalizeScope(scope);
        if (resolved == null) {
            throw violation("adaptor canonical scope must not be null");
        }
        return resolved;
    }

    @Override
    public ScanScope resolveLiveScope(Connection connection, ScanScope scope) {
        ScanScope resolved = delegate.resolveLiveScope(connection, scope);
        if (resolved == null) {
            throw violation("adaptor live scope must not be null");
        }
        return resolved;
    }

    @Override
    public Set<Integer> permissionDeniedVendorCodes() {
        return permissionDeniedVendorCodes;
    }

    @Override
    public AdaptorCollectors collectors() {
        return collectors;
    }

    @Override
    public AdaptorParsers parsers() {
        return parsers;
    }

    @Override
    public AdaptorProfiling profiling() {
        return profiling;
    }
}
