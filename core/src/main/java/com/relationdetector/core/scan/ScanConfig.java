package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.OfflineSampleCompleteness;
import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.contracts.spi.DataProfileOptions;

/** Runtime configuration after YAML and CLI overrides are resolved. */
public final class ScanConfig {
    public DatabaseType databaseType;
    public String adaptorId;
    public String jdbcUrl;
    public String username;
    public String password;
    public String catalog;
    public String schema;
    public List<String> includeTables = new ArrayList<>();
    public List<String> excludeTables = new ArrayList<>();
    public boolean metadataEnabled = true;
    public boolean ddlEnabled;
    public boolean ddlFromDatabase = true;
    public List<Path> ddlFiles = new ArrayList<>();
    public List<Path> ddlPaths = new ArrayList<>();
    public List<String> ddlIncludes = new ArrayList<>();
    public boolean objectsEnabled;
    public boolean objectsFromDatabase;
    public List<Path> objectFiles = new ArrayList<>();
    public List<Path> objectPaths = new ArrayList<>();
    public List<String> objectIncludes = new ArrayList<>();
    public boolean logsEnabled;
    public List<Path> logFiles = new ArrayList<>();
    public List<Path> logPaths = new ArrayList<>();
    public List<String> logIncludes = new ArrayList<>();
    public LogFormatHint logFormatHint = LogFormatHint.AUTO;
    public boolean logsFilterSystemQueries = true;
    public List<String> logSystemSchemas = new ArrayList<>();
    public List<String> logMetadataQueryMarkers = new ArrayList<>();
    public boolean dataProfileEnabled;
    public int sampleRows = 10_000;
    public int timeoutSeconds = 30;
    public int maxCandidatePairs = 1_000;
    public int maxDistinctValues = 5_000;
    public int maxTargetsPerSourceColumn = 3;
    public double minContainmentRatio = 0.98d;
    public double minOverlapRatio = 0.80d;
    public double maxMismatchRatio = 0.50d;
    public int minDistinctValues = 20;
    public int minRowsForNegative = 100;
    public boolean verifyDeclaredForeignKeys;
    public boolean discoverFromNamingEvidence;
    public boolean useOfflineInsertSamples = true;
    public OfflineSampleCompleteness offlineSampleCompleteness = OfflineSampleCompleteness.PARTIAL;
    public boolean skipUnindexedLargeTargets = true;
    public OutputFormat outputFormat = OutputFormat.JSON;
    public double minConfidence = 0.30d;
    public boolean includeEvidence = true;
    public boolean includeWarnings = true;
    public boolean includeObservationCounts = true;
    public String parserMode = "auto";
    public String grammarProfile = "";
    public String databaseVersion = "";
    public String databaseVersionSource = "UNKNOWN";

    public DataProfileOptions dataProfileOptions() {
        return new DataProfileOptions(
                sampleRows,
                timeoutSeconds,
                maxCandidatePairs,
                maxDistinctValues,
                maxTargetsPerSourceColumn,
                minContainmentRatio,
                minOverlapRatio,
                maxMismatchRatio,
                minDistinctValues,
                minRowsForNegative,
                verifyDeclaredForeignKeys,
                discoverFromNamingEvidence,
                useOfflineInsertSamples,
                offlineSampleCompleteness,
                skipUnindexedLargeTargets);
    }
}
