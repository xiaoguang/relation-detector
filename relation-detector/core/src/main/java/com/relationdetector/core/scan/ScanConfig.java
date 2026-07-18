package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.core.naming.NamingRule;
import com.relationdetector.core.naming.NamingRuleSet;

/**
 *
 * Mutable YAML/CLI input DTO. Production scans immediately snapshot it into a
 * {@link ResolvedScanConfig}; callers may keep using this class to assemble
 * configuration without exposing mutable state to a running scan.
 */
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
    public int timeoutSeconds = 30;
    public int maxCandidatePairs = 1_000;
    public int maxTargetsPerSourceColumn = 3;
    public double minContainmentRatio = 0.98d;
    public double minOverlapRatio = 0.80d;
    public double maxMismatchRatio = 0.50d;
    public int minDistinctValues = 20;
    public int minRowsForNegative = 100;
    public boolean verifyDeclaredForeignKeys;
    public boolean discoverFromNamingEvidence;
    public boolean skipUnindexedLargeTargets = true;
    public OutputFormat outputFormat = OutputFormat.JSON;
    public double minConfidence = 0.30d;
    public boolean includeEvidence = true;
    public boolean includeWarnings = true;
    public boolean includeObservationCounts = true;
    /** Maximum number of independent file/object/log parse tasks in one scan. */
    public int executionParallelism = 1;
    public boolean namingMatchEnabled = true;
    public boolean namingMatchSystemRulesEnabled = true;
    public List<Path> namingMatchRuleFiles = new ArrayList<>();
    public List<NamingRule> namingMatchRules = new ArrayList<>();
    public boolean derivedPathsEnabled;
    public boolean derivedRelationshipsEnabled = true;
    public boolean derivedDataLineageEnabled = true;
    public boolean derivedNamingEvidenceEnabled = true;
    public boolean derivedIncludeNamingEdgesInRelationshipPaths = true;
    public int derivedMaxPathLength = 5;
    public int derivedMaxPathsPerPair;
    public int derivedMaxFacts;
    public double derivedConfidenceDecay = 0.75d;
    public double derivedMinConfidence = 0.10d;
    public String parserMode = "auto";
    public String grammarProfile = "";
    public String databaseVersion = "";
    public String databaseVersionSource = "UNKNOWN";

    public DataProfileOptions dataProfileOptions() {
        return new DataProfileOptions(
                timeoutSeconds,
                maxCandidatePairs,
                maxTargetsPerSourceColumn,
                minContainmentRatio,
                minOverlapRatio,
                maxMismatchRatio,
                minDistinctValues,
                minRowsForNegative,
                verifyDeclaredForeignKeys,
                discoverFromNamingEvidence,
                skipUnindexedLargeTargets);
    }

    public NamingRuleSet namingRuleSet() {
        return new NamingRuleSetResolver().ruleSet(
                namingMatchEnabled, namingMatchSystemRulesEnabled,
                namingMatchRuleFiles, namingMatchRules);
    }

    public ResolvedScanConfig resolve() {
        return resolve(Path.of(""));
    }

    /** Resolves file inputs relative to the caller's configuration base directory. */
    public ResolvedScanConfig resolve(Path baseDirectory) {
        return ResolvedScanConfig.from(this, baseDirectory);
    }
}
