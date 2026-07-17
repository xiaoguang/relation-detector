package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.relationdetector.core.scan.ScanConfig;
import com.relationdetector.core.scan.ScanEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdaptorApiVersionTest {
    @Test
    void builtInAdaptorsUseCurrentApi() throws Exception {
        for (DatabaseType type : DatabaseType.values()) {
            DatabaseAdaptor adaptor = AdaptorRegistry.load(null).resolve(type, null);
            assertEquals(AdaptorApiVersion.CURRENT, adaptor.spiVersion(), adaptor.id());
        }
    }

    @Test
    void v5PluginIsRejectedWithV6RecompileGuidance() {
        DatabaseAdaptor legacy = new ContractAdaptor(5, "legacy-v5", Set.of(DatabaseType.MYSQL));

        AdaptorRegistry.AdaptorException error = assertThrows(AdaptorRegistry.AdaptorException.class,
                () -> registry(legacy).resolve(DatabaseType.MYSQL, null));

        assertTrue(error.getMessage().contains("actual=5"));
        assertTrue(error.getMessage().contains("required=6"));
        assertTrue(error.getMessage().contains("recompile"));
    }

    static Stream<Arguments> invalidAdaptorContracts() {
        return Stream.of(
                Arguments.of(new ContractAdaptor(AdaptorApiVersion.CURRENT - 1, "legacy-test",
                        Set.of(DatabaseType.MYSQL)), DatabaseType.MYSQL, null),
                Arguments.of(new ContractAdaptor(AdaptorApiVersion.CURRENT, "type-test",
                        Set.of(DatabaseType.POSTGRESQL)), DatabaseType.MYSQL, null),
                Arguments.of(new ContractAdaptor(AdaptorApiVersion.CURRENT, "actual-id",
                        Set.of(DatabaseType.MYSQL)), DatabaseType.MYSQL, "configured-id"));
    }

    @ParameterizedTest
    @MethodSource("invalidAdaptorContracts")
    void registryAndDirectEngineUseTheSameAdaptorContractConclusion(
            DatabaseAdaptor adaptor,
            DatabaseType databaseType,
            String adaptorId
    ) throws Exception {
        ScanConfig config = new ScanConfig();
        config.databaseType = databaseType;
        config.adaptorId = adaptorId;
        config.metadataEnabled = false;
        config.logsEnabled = true;
        Path logFile = Files.createTempFile("adaptor-contract", ".sql");
        Files.writeString(logFile, "SELECT 1;");
        config.logFiles.add(logFile);

        IllegalArgumentException directError = assertThrows(IllegalArgumentException.class,
                () -> new ScanEngine().scan(config, adaptor));
        AdaptorRegistry.AdaptorException registryError = assertThrows(AdaptorRegistry.AdaptorException.class,
                () -> registry(adaptor).resolve(databaseType, adaptorId));

        assertEquals(directError.getMessage(), registryError.getMessage());
        assertEquals(directError.getMessage(), registryError.getCause().getMessage());
        assertInstanceOf(IllegalArgumentException.class, registryError.getCause());
    }

    private AdaptorRegistry registry(DatabaseAdaptor adaptor) {
        return new AdaptorRegistry(List.of(adaptor));
    }

    private record ContractAdaptor(
            int spiVersion,
            String id,
            Set<DatabaseType> supportedDatabaseTypes
    ) implements DatabaseAdaptor {

        @Override
        public String displayName() {
            return id;
        }

        @Override
        public Set<AdaptorCapability> capabilities() {
            return Set.of();
        }

        @Override
        public IdentifierRules identifierRules() {
            throw new AssertionError("invalid adaptor capabilities must not be called");
        }

        @Override
        public AdaptorCollectors collectors() {
            throw new AssertionError("invalid adaptor capabilities must not be called");
        }

        @Override
        public AdaptorParsers parsers() {
            throw new AssertionError("invalid adaptor capabilities must not be called");
        }

        @Override
        public AdaptorProfiling profiling() {
            throw new AssertionError("invalid adaptor capabilities must not be called");
        }
    }
}
