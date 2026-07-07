package com.relationdetector.core.relation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NamingRuleSet {
    private static final NamingRuleSet SYSTEM_DEFAULT =
            new NamingRuleConfigLoader().loadSystemDefault();

    private final List<NamingRule> rules;

    public NamingRuleSet(List<NamingRule> rules) {
        this.rules = List.copyOf(rules == null ? List.of() : rules);
        validateUniqueIds(this.rules);
    }

    public static NamingRuleSet systemDefault() {
        return SYSTEM_DEFAULT;
    }

    public static NamingRuleSet fromConfig(
            boolean enabled,
            boolean systemRulesEnabled,
            List<NamingRule> configuredRules
    ) {
        if (!enabled) {
            return new NamingRuleSet(List.of());
        }
        List<NamingRule> merged = new ArrayList<>();
        if (systemRulesEnabled) {
            merged.addAll(systemDefault().rules());
        }
        if (configuredRules != null) {
            merged.addAll(configuredRules);
        }
        return new NamingRuleSet(merged);
    }

    public List<NamingRule> rules() {
        return rules;
    }

    private static void validateUniqueIds(List<NamingRule> rules) {
        Map<String, NamingRule> byId = new LinkedHashMap<>();
        for (NamingRule rule : rules) {
            NamingRule previous = byId.putIfAbsent(rule.id(), rule);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate namingMatch rule id: " + rule.id());
            }
        }
    }
}
