package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.List;

import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.core.naming.NamingRule;
import com.relationdetector.core.naming.NamingRuleSet;

/** Immutable evidence extraction, profiling, and derived-path configuration. */
public record EvidenceConfig(
        boolean dataProfileEnabled,
        DataProfileOptions dataProfileOptions,
        boolean namingMatchEnabled,
        boolean namingMatchSystemRulesEnabled,
        List<Path> namingMatchRuleFiles,
        List<NamingRule> namingMatchRules,
        boolean derivedPathsEnabled,
        boolean derivedRelationshipsEnabled,
        boolean derivedDataLineageEnabled,
        boolean derivedNamingEvidenceEnabled,
        boolean derivedIncludeNamingEdgesInRelationshipPaths,
        int derivedMaxPathLength,
        int derivedMaxPathsPerPair,
        int derivedMaxFacts,
        double derivedConfidenceDecay,
        double derivedMinConfidence
) {
    public EvidenceConfig {
        namingMatchRuleFiles = List.copyOf(namingMatchRuleFiles == null ? List.of() : namingMatchRuleFiles);
        namingMatchRules = List.copyOf(namingMatchRules == null ? List.of() : namingMatchRules);
    }

    public NamingRuleSet namingRuleSet() {
        return NamingRuleSet.fromConfig(namingMatchEnabled, namingMatchSystemRulesEnabled, namingMatchRules);
    }
}
