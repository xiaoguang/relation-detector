package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.spi.AdaptorApiVersion;
import com.relationdetector.contracts.spi.AdaptorCollectors;
import com.relationdetector.contracts.spi.AdaptorParsers;
import com.relationdetector.contracts.spi.AdaptorProfiling;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdaptorApiVersionTest {
    @Test
    void builtInAdaptorsUseCurrentApi() throws Exception {
        for (DatabaseType type : DatabaseType.values()) {
            DatabaseAdaptor adaptor = AdaptorRegistry.load(null).resolve(type, null);
            assertEquals(AdaptorApiVersion.CURRENT, adaptor.spiVersion(), adaptor.id());
        }
    }

    @Test
    void legacyPluginIsRejectedBeforeCapabilitiesAreUsed() {
        DatabaseAdaptor legacy = new LegacyAdaptor();

        AdaptorRegistry.AdaptorException error = assertThrows(
                AdaptorRegistry.AdaptorException.class,
                () -> AdaptorRegistry.requireCurrentApi(legacy));

        assertTrue(error.getMessage().contains("legacy-test"));
        assertTrue(error.getMessage().contains("actual=1"));
        assertTrue(error.getMessage().contains("required=2"));
        assertTrue(error.getMessage().contains("recompile"));
    }

    private static final class LegacyAdaptor implements DatabaseAdaptor {
        @Override
        public String id() {
            return "legacy-test";
        }

        @Override
        public String displayName() {
            return "Legacy test adaptor";
        }

        @Override
        public Set<DatabaseType> supportedDatabaseTypes() {
            return Set.of(DatabaseType.MYSQL);
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            throw new AssertionError("legacy adaptor capabilities must not be called");
        }

        @Override
        public AdaptorCollectors collectors() {
            throw new AssertionError("legacy adaptor capabilities must not be called");
        }

        @Override
        public AdaptorParsers parsers() {
            throw new AssertionError("legacy adaptor capabilities must not be called");
        }

        @Override
        public AdaptorProfiling profiling() {
            throw new AssertionError("legacy adaptor capabilities must not be called");
        }

    }
}
