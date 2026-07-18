package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.core.naming.NamingRule;
import com.relationdetector.core.naming.NamingRuleConfigLoader;
import com.relationdetector.core.naming.NamingRuleSet;

/**
 * CN: 解析 naming rule file 路径、加载用户规则并与 inline rules 合并为 scan 唯一不可变规则集。
 * EN: Resolves rule-file paths and merges file and inline naming rules into the single immutable rule set for a scan.
 */
final class NamingRuleSetResolver {
    private final NamingRuleConfigLoader loader = new NamingRuleConfigLoader();

    List<Path> resolvePaths(List<Path> files, Path baseDirectory) {
        Path base = baseDirectory == null ? Path.of("") : baseDirectory;
        return (files == null ? List.<Path>of() : files).stream()
                .map(path -> path.isAbsolute() ? path : base.resolve(path))
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    List<NamingRule> configuredRules(List<Path> files, List<NamingRule> inlineRules) {
        try {
            List<NamingRule> combined = new ArrayList<>();
            for (Path file : files == null ? List.<Path>of() : files) {
                combined.addAll(loader.loadRuleFile(file));
            }
            combined.addAll(inlineRules == null ? List.of() : inlineRules);
            new NamingRuleSet(combined);
            return List.copyOf(combined);
        } catch (IllegalArgumentException ex) {
            throw new ScanConfigurationException("invalid namingMatch rules: " + ex.getMessage(), ex);
        }
    }

    NamingRuleSet ruleSet(
            boolean enabled,
            boolean systemRulesEnabled,
            List<Path> files,
            List<NamingRule> inlineRules
    ) {
        try {
            return NamingRuleSet.fromConfig(
                    enabled,
                    systemRulesEnabled,
                    configuredRules(resolvePaths(files, Path.of("")), inlineRules));
        } catch (IllegalArgumentException ex) {
            if (ex instanceof ScanConfigurationException configurationException) {
                throw configurationException;
            }
            throw new ScanConfigurationException("invalid namingMatch rules: " + ex.getMessage(), ex);
        }
    }
}
