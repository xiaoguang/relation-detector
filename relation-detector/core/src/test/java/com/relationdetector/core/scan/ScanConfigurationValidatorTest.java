package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.DatabaseType;

class ScanConfigurationValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsLiveMetadataWithoutJdbc() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;

        assertThrows(ScanConfigurationException.class, config::resolve);
    }

    @Test
    void rejectsEveryLiveFeatureWithoutJdbc() {
        ScanConfig ddl = fileOnlyBase();
        ddl.ddlEnabled = true;
        ddl.ddlFromDatabase = true;
        assertThrows(ScanConfigurationException.class, ddl::resolve);

        ScanConfig objects = fileOnlyBase();
        objects.objectsEnabled = true;
        objects.objectsFromDatabase = true;
        assertThrows(ScanConfigurationException.class, objects::resolve);

        ScanConfig profile = fileOnlyBase();
        profile.dataProfileEnabled = true;
        profile.logsEnabled = true;
        profile.logFiles.add(writeSql("profile-source.sql"));
        assertThrows(ScanConfigurationException.class, profile::resolve);
    }

    @Test
    void rejectsWhenEveryExecutableSourceIsDisabled() {
        ScanConfig config = fileOnlyBase();

        assertThrows(IllegalArgumentException.class, config::resolve);
    }

    @Test
    void rejectsEnabledFileSourceWithoutResolvedFiles() {
        ScanConfig config = fileOnlyBase();
        config.logsEnabled = true;

        assertThrows(IllegalArgumentException.class, config::resolve);
    }

    @Test
    void acceptsFileOnlySourceWithoutJdbc() throws Exception {
        Path log = tempDir.resolve("query.sql");
        Files.writeString(log, "SELECT 1;\n");
        ScanConfig config = fileOnlyBase();
        config.logsEnabled = true;
        config.logFiles.add(log);

        assertDoesNotThrow(() -> config.resolve());
    }

    @Test
    void ignoresResidualPathsForDisabledSources() {
        ScanConfig config = validLiveConfig();
        config.ddlEnabled = false;
        config.ddlFiles.add(tempDir.resolve("missing-ddl.sql"));
        config.objectsEnabled = false;
        config.objectFiles.add(tempDir.resolve("missing-object.sql"));
        config.logsEnabled = false;
        config.logFiles.add(tempDir.resolve("missing-log.sql"));

        assertDoesNotThrow(() -> config.resolve());
    }

    @Test
    void acceptsClosedConfidenceBoundaries() {
        ScanConfig zero = validLiveConfig();
        zero.minConfidence = 0.0d;
        ScanConfig one = validLiveConfig();
        one.minConfidence = 1.0d;

        assertDoesNotThrow(() -> zero.resolve());
        assertDoesNotThrow(() -> one.resolve());
    }

    @Test
    void rejectsUnknownParserModeFromDirectApi() {
        ScanConfig config = validLiveConfig();
        config.parserMode = "other";

        assertThrows(IllegalArgumentException.class, config::resolve);
    }

    @Test
    void rejectsIllegalDerivedLimitsFromDirectApi() {
        ScanConfig config = validLiveConfig();
        config.derivedMaxPathLength = 0;

        assertThrows(IllegalArgumentException.class, config::resolve);

        config.derivedMaxPathLength = 5;
        config.derivedMaxPathsPerPair = -1;
        assertThrows(IllegalArgumentException.class, config::resolve);

        config.derivedMaxPathsPerPair = 0;
        config.derivedMaxFacts = -1;
        assertThrows(IllegalArgumentException.class, config::resolve);

        config.derivedMaxFacts = 0;
        config.derivedConfidenceDecay = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::resolve);

        config.derivedConfidenceDecay = 0.75d;
        config.derivedMinConfidence = Double.NEGATIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, config::resolve);
    }

    @Test
    void rejectsOutputConfidenceOutsideClosedUnitInterval() {
        for (double value : new double[] {-0.01d, 1.01d, Double.NaN, Double.POSITIVE_INFINITY}) {
            ScanConfig config = validLiveConfig();
            config.minConfidence = value;

            assertThrows(IllegalArgumentException.class, config::resolve, "value=" + value);
        }
    }

    @Test
    void rejectsInvalidResolvedConfigCreatedDirectly() {
        ResolvedScanConfig valid = validLiveConfig().resolve();

        assertThrows(IllegalArgumentException.class, () -> new ResolvedScanConfig(
                valid.database(),
                valid.sources(),
                new ParserConfig("unknown", "", "", "UNKNOWN"),
                valid.evidence(),
                valid.execution(),
                valid.output()));
    }

    private ScanConfig fileOnlyBase() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.COMMON;
        config.metadataEnabled = false;
        config.ddlEnabled = false;
        config.objectsEnabled = false;
        config.logsEnabled = false;
        return config;
    }

    private ScanConfig validLiveConfig() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = "jdbc:test:runtime-validation";
        config.metadataEnabled = true;
        return config;
    }

    private Path writeSql(String name) {
        try {
            Path file = tempDir.resolve(name);
            Files.writeString(file, "SELECT 1;\n");
            return file;
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }
}
