package com.relationdetector.core.relation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class NamingRuleConfigLoader {
    private static final YAMLMapper YAML = new YAMLMapper();
    private static final String SYSTEM_DEFAULT = "/naming-rules/system-default.yml";

    public NamingRuleSet loadSystemDefault() {
        try (InputStream input = NamingRuleConfigLoader.class.getResourceAsStream(SYSTEM_DEFAULT)) {
            if (input == null) {
                throw new IllegalStateException("missing " + SYSTEM_DEFAULT);
            }
            return new NamingRuleSet(readRules(YAML.readTree(input), "system"));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load " + SYSTEM_DEFAULT, ex);
        }
    }

    public List<NamingRule> loadRuleFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("namingMatch rule file does not exist: " + file);
            }
            return readRules(YAML.readTree(file.toFile()), file.toString());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to load namingMatch rule file: " + file, ex);
        }
    }

    public List<NamingRule> readInlineRules(JsonNode rules) {
        return readRulesArray(rules, "inline");
    }

    private List<NamingRule> readRules(JsonNode root, String source) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        JsonNode rules = root.has("rules") ? root.path("rules") : root;
        return readRulesArray(rules, source);
    }

    private List<NamingRule> readRulesArray(JsonNode rules, String source) {
        if (rules == null || rules.isMissingNode() || rules.isNull()) {
            return List.of();
        }
        if (!rules.isArray()) {
            throw new IllegalArgumentException("namingMatch rules must be an array");
        }
        java.util.ArrayList<NamingRule> result = new java.util.ArrayList<>();
        for (JsonNode rule : rules) {
            result.add(readRule(rule, source));
        }
        return List.copyOf(result);
    }

    private NamingRule readRule(JsonNode node, String source) {
        String id = text(node, "id");
        String rule = text(node, "rule");
        Set<NamingRuleScope> scopes = scopes(node.path("appliesTo"));
        boolean directionHint = !node.has("directionHint") || node.path("directionHint").asBoolean();
        boolean requireSelfJoinRole = node.path("requireSelfJoinRole").asBoolean(false);
        String description = text(node, "description");
        NamingRule.EndpointMatcher sourceMatcher = endpointMatcher(node, "source");
        NamingRule.EndpointMatcher targetMatcher = endpointMatcher(node, "target");
        NamingRule namingRule = new NamingRule(id, rule, scopes, sourceMatcher, targetMatcher,
                directionHint, requireSelfJoinRole, description, source);
        validateRule(namingRule);
        return namingRule;
    }

    private NamingRule.EndpointMatcher endpointMatcher(JsonNode node, String prefix) {
        String endpoint = text(node, prefix + "Endpoint");
        JsonNode endpointNode = node.path(prefix);
        if (endpoint.isBlank() && endpointNode.isTextual()) {
            endpoint = endpointNode.asText();
        }
        return new NamingRule.EndpointMatcher(
                endpoint,
                identifierMatcher(node.path(prefix + "Table")),
                identifierMatcher(node.path(prefix + "Column")));
    }

    private NamingRule.IdentifierMatcher identifierMatcher(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return NamingRule.IdentifierMatcher.empty();
        }
        return new NamingRule.IdentifierMatcher(
                text(node, "equals"),
                stringList(node.path("equalsAny")),
                text(node, "suffix"),
                stringList(node.path("suffixAny")),
                stringList(node.path("aliases")));
    }

    private Set<NamingRuleScope> scopes(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Set.of(NamingRuleScope.RELATIONSHIP_CANDIDATE);
        }
        Set<NamingRuleScope> scopes = new LinkedHashSet<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                scopes.add(scope(item.asText()));
            }
        } else {
            scopes.add(scope(node.asText()));
        }
        return scopes;
    }

    private NamingRuleScope scope(String value) {
        return NamingRuleScope.valueOf(value.trim().toUpperCase().replace("-", "_"));
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            java.util.ArrayList<String> result = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                result.add(item.asText());
            }
            return List.copyOf(result);
        }
        return List.of(node.asText());
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private void validateRule(NamingRule rule) {
        if (!"system".equals(rule.ruleSource()) && !"USER_CONFIGURED".equals(rule.rule())) {
            throw new IllegalArgumentException("configured namingMatch rule " + rule.id()
                    + " must use rule USER_CONFIGURED");
        }
        if (!"USER_CONFIGURED".equals(rule.rule())) {
            return;
        }
        boolean hasSource = !rule.sourceMatcher().isEmpty();
        boolean hasTarget = !rule.targetMatcher().isEmpty();
        if (!rule.hasExplicitPair() && (!hasSource || !hasTarget)) {
            throw new IllegalArgumentException("USER_CONFIGURED namingMatch rule " + rule.id()
                    + " must define both source and target matchers, or an explicit endpoint pair");
        }
    }
}
