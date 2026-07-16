package com.relationdetector.cli;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 *
 * Jackson YAML transport model. Defaults remain owned by ScanConfig.
 */
final class ScanYamlConfigDto {
    public Database database = new Database();
    public Sources sources = new Sources();
    public Execution execution = new Execution();
    public Filters filters = new Filters();
    public Output output = new Output();
    public NamingMatch namingMatch = new NamingMatch();
    public DerivedPaths derivedPaths = new DerivedPaths();
    public Parser parser = new Parser();

    static final class Database {
        public String type;
        public String adaptorId;
        public String jdbcUrl;
        public String username;
        public String password;
        public String schema;
        public String catalog;
    }

    static final class Sources {
        public Metadata metadata = new Metadata();
        public SqlSource ddl = new SqlSource();
        public SqlSource objects = new SqlSource();
        public Logs logs = new Logs();
        public DataProfile dataProfile = new DataProfile();
    }

    static final class Metadata {
        public Boolean enabled;
    }

    static class SqlSource {
        public Boolean enabled;
        public Boolean fromDatabase;
        public List<String> files = new ArrayList<>();
        public List<String> paths = new ArrayList<>();
        public List<String> include = new ArrayList<>();
    }

    static final class Logs extends SqlSource {
        public String format;
        public Boolean filterSystemQueries;
        public List<String> systemSchemas = new ArrayList<>();
        public List<String> metadataQueryMarkers = new ArrayList<>();
    }

    static final class DataProfile {
        public Boolean enabled;
        public Integer sampleRows;
        public Integer timeoutSeconds;
        public Integer maxCandidatePairs;
        public Integer maxDistinctValues;
        public Integer maxTargetsPerSourceColumn;
        public Double minContainmentRatio;
        public Double minOverlapRatio;
        public Double maxMismatchRatio;
        public Integer minDistinctValues;
        public Integer minRowsForNegative;
        public Boolean verifyDeclaredForeignKeys;
        public Boolean discoverFromNamingEvidence;
        public Boolean useOfflineInsertSamples;
        public String offlineSampleCompleteness;
        public Boolean skipUnindexedLargeTargets;
    }

    static final class Execution {
        public Integer parallelism;
    }

    static final class Filters {
        public List<String> includeTables = new ArrayList<>();
        public List<String> excludeTables = new ArrayList<>();
    }

    static final class Output {
        public String format;
        public Double minConfidence;
        public Boolean includeEvidence;
        public Boolean includeWarnings;
        public Boolean includeObservationCounts;
    }

    static final class NamingMatch {
        public Boolean enabled;
        public Boolean systemRulesEnabled;
        public List<String> ruleFiles = new ArrayList<>();
        public JsonNode rules;
    }

    static final class DerivedPaths {
        public Boolean enabled;
        public Boolean relationships;
        public Boolean dataLineage;
        public Boolean namingEvidence;
        public Boolean includeNamingEdgesInRelationshipPaths;
        public Integer maxPathLength;
        public Integer maxPathsPerPair;
        public Integer maxFacts;
        public Double confidenceDecay;
        public Double minConfidence;
    }

    static final class Parser {
        public String mode;
        public String grammarProfile;
        public String databaseVersion;
        public RemovedParserConfig sql;
        public RemovedParserConfig ddl;
    }

    static final class RemovedParserConfig {
        public String mode;
        public Boolean fallbackOnFailure;
    }
}
