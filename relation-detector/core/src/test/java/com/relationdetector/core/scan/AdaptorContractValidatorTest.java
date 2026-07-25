package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class AdaptorContractValidatorTest {
    private final AdaptorContractValidator validator = new AdaptorContractValidator();
    private final DatabaseConfig database = new DatabaseConfig(
            DatabaseType.COMMON, "shape-test", null, null, null, null, null, List.of(), List.of());

    @Test
    void rejectsEveryNullTopLevelAdaptorShapeAsContractViolation() {
        for (BrokenField field : BrokenField.values()) {
            assertThrows(AdaptorContractException.class,
                    () -> validator.validate(database, new ShapeAdaptor(field)),
                    field.name());
        }
    }

    @Test
    void returnsOneImmutableShapeSnapshotInsteadOfRereadingPluginGetters() throws Exception {
        ShapeAdaptor plugin = new ShapeAdaptor(null);
        Method validate = AdaptorContractValidator.class.getMethod(
                "validate", DatabaseConfig.class, DatabaseAdaptor.class);

        DatabaseAdaptor snapshot = assertInstanceOf(
                DatabaseAdaptor.class,
                validate.invoke(validator, database, plugin));
        plugin.rejectFurtherShapeReads = true;
        plugin.supportedTypes.clear();
        plugin.capabilitySet.clear();

        assertEquals(Set.of(DatabaseType.COMMON), snapshot.supportedDatabaseTypes());
        assertEquals(Set.of(AdaptorCapability.DDL_PARSING, AdaptorCapability.NATIVE_LOGS),
                snapshot.capabilities());
        snapshot.identifierRules();
        snapshot.collectors();
        snapshot.parsers();
        snapshot.profiling();
        assertEquals(1, plugin.supportedTypesReads.get());
        assertEquals(1, plugin.capabilityReads.get());
        assertEquals(1, plugin.identifierRuleReads.get());
        assertEquals(1, plugin.collectorReads.get());
        assertEquals(1, plugin.parserReads.get());
        assertEquals(1, plugin.profilingReads.get());
    }

    @Test
    void validatedAdaptorRejectsNullCanonicalAndLiveScopes() throws Exception {
        ShapeAdaptor plugin = new ShapeAdaptor(null);
        Method validate = AdaptorContractValidator.class.getMethod(
                "validate", DatabaseConfig.class, DatabaseAdaptor.class);
        DatabaseAdaptor snapshot = assertInstanceOf(
                DatabaseAdaptor.class,
                validate.invoke(validator, database, plugin));
        ScanScope scope = new ScanScope(null, null, List.of(), List.of());

        plugin.nullCanonicalScope = true;
        assertThrows(AdaptorContractException.class, () -> snapshot.canonicalizeScope(scope));
        plugin.nullCanonicalScope = false;
        plugin.nullLiveScope = true;
        assertThrows(AdaptorContractException.class, () -> snapshot.resolveLiveScope(null, scope));
    }

    private enum BrokenField {
        SUPPORTED_DATABASE_TYPES,
        CAPABILITIES,
        IDENTIFIER_RULES,
        COLLECTORS,
        PARSERS,
        PROFILING,
        PROFILING_DATA_PROFILER,
        PROFILING_WEIGHT_ADJUSTER,
        PERMISSION_VENDOR_CODES
    }

    private static final class ShapeAdaptor implements DatabaseAdaptor {
        private final CommonDatabaseAdaptor delegate = new CommonDatabaseAdaptor();
        private final BrokenField broken;
        private final Set<DatabaseType> supportedTypes = new HashSet<>(Set.of(DatabaseType.COMMON));
        private final Set<AdaptorCapability> capabilitySet = new HashSet<>(
                Set.of(AdaptorCapability.DDL_PARSING, AdaptorCapability.NATIVE_LOGS));
        private final AtomicInteger supportedTypesReads = new AtomicInteger();
        private final AtomicInteger capabilityReads = new AtomicInteger();
        private final AtomicInteger identifierRuleReads = new AtomicInteger();
        private final AtomicInteger collectorReads = new AtomicInteger();
        private final AtomicInteger parserReads = new AtomicInteger();
        private final AtomicInteger profilingReads = new AtomicInteger();
        private boolean rejectFurtherShapeReads;
        private boolean nullCanonicalScope;
        private boolean nullLiveScope;

        private ShapeAdaptor(BrokenField broken) {
            this.broken = broken;
        }

        @Override
        public int spiVersion() {
            return delegate.spiVersion();
        }

        @Override
        public String id() {
            return "shape-test";
        }

        @Override
        public String displayName() {
            return "shape-test";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            rejectRepeatedRead(supportedTypesReads);
            return broken == BrokenField.SUPPORTED_DATABASE_TYPES ? null : supportedTypes;
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            rejectRepeatedRead(capabilityReads);
            return broken == BrokenField.CAPABILITIES
                    ? null
                    : capabilitySet;
        }

        @Override
        public IdentifierRules identifierRules() {
            rejectRepeatedRead(identifierRuleReads);
            return broken == BrokenField.IDENTIFIER_RULES ? null : delegate.identifierRules();
        }

        @Override
        public AdaptorCollectors collectors() {
            rejectRepeatedRead(collectorReads);
            return broken == BrokenField.COLLECTORS ? null : delegate.collectors();
        }

        @Override
        public AdaptorParsers parsers() {
            rejectRepeatedRead(parserReads);
            return broken == BrokenField.PARSERS ? null : delegate.parsers();
        }

        @Override
        public AdaptorProfiling profiling() {
            rejectRepeatedRead(profilingReads);
            if (broken == BrokenField.PROFILING) {
                return null;
            }
            if (broken == BrokenField.PROFILING_DATA_PROFILER) {
                return new AdaptorProfiling(null, delegate.profiling().evidenceWeightAdjuster());
            }
            if (broken == BrokenField.PROFILING_WEIGHT_ADJUSTER) {
                return new AdaptorProfiling(delegate.profiling().dataProfiler(), null);
            }
            return delegate.profiling();
        }

        @Override
        public Set<Integer> permissionDeniedVendorCodes() {
            return broken == BrokenField.PERMISSION_VENDOR_CODES ? null : Set.of();
        }

        @Override
        public ScanScope canonicalizeScope(ScanScope scope) {
            return nullCanonicalScope ? null : scope;
        }

        @Override
        public ScanScope resolveLiveScope(Connection connection, ScanScope scope) {
            return nullLiveScope ? null : scope;
        }

        private void rejectRepeatedRead(AtomicInteger counter) {
            counter.incrementAndGet();
            if (rejectFurtherShapeReads) {
                throw new AssertionError("validated adaptor reread plugin shape");
            }
        }
    }
}
