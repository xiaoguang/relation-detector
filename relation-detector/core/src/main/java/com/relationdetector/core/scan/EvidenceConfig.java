package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.List;

import com.relationdetector.contracts.spi.DataProfileOptions;
import com.relationdetector.core.naming.NamingRule;
import com.relationdetector.core.naming.NamingRuleSet;

/**
 * CN: 承载 naming、live profiling 与 derived-path 推导的不可变配置，其 rule files 已在 scan 前解析。
 * EN: Carries immutable naming, live-profiling, and derived-path configuration after rule files have been resolved before scanning.
 */
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
        namingMatchRules = new NamingRuleSetResolver().configuredRules(
                namingMatchRuleFiles, namingMatchRules == null ? List.of() : namingMatchRules);
        NamingRuleSet.fromConfig(namingMatchEnabled, namingMatchSystemRulesEnabled, namingMatchRules);
    }

    public NamingRuleSet namingRuleSet() {
        return NamingRuleSet.fromConfig(namingMatchEnabled, namingMatchSystemRulesEnabled, namingMatchRules);
    }
}
