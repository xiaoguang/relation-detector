package com.relationdetector.core.relation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record NamingRule(
        String id,
        String rule,
        Set<NamingRuleScope> appliesTo,
        EndpointMatcher sourceMatcher,
        EndpointMatcher targetMatcher,
        boolean directionHint,
        boolean requireSelfJoinRole,
        String description,
        String ruleSource
) {
    public NamingRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("namingMatch rule id is required");
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("namingMatch rule is required for " + id);
        }
        if ("TRANSITIVE_NAMING_PATH".equals(rule)) {
            throw new IllegalArgumentException("TRANSITIVE_NAMING_PATH is derived-only and cannot be configured");
        }
        appliesTo = appliesTo == null || appliesTo.isEmpty()
                ? Set.of(NamingRuleScope.RELATIONSHIP_CANDIDATE)
                : Set.copyOf(appliesTo);
        sourceMatcher = sourceMatcher == null ? EndpointMatcher.empty() : sourceMatcher;
        targetMatcher = targetMatcher == null ? EndpointMatcher.empty() : targetMatcher;
        description = description == null ? "" : description;
        ruleSource = ruleSource == null || ruleSource.isBlank() ? "configured" : ruleSource;
    }

    boolean appliesTo(NamingRuleScope scope) {
        return appliesTo.contains(scope);
    }

    boolean hasExplicitPair() {
        return !sourceMatcher.endpoint().isBlank() && !targetMatcher.endpoint().isBlank();
    }

    public record EndpointMatcher(
            String endpoint,
            IdentifierMatcher table,
            IdentifierMatcher column
    ) {
        static EndpointMatcher empty() {
            return new EndpointMatcher("", IdentifierMatcher.empty(), IdentifierMatcher.empty());
        }

        public EndpointMatcher {
            endpoint = normalize(endpoint);
            table = table == null ? IdentifierMatcher.empty() : table;
            column = column == null ? IdentifierMatcher.empty() : column;
        }

        boolean isEmpty() {
            return endpoint.isBlank() && table.isEmpty() && column.isEmpty();
        }
    }

    public record IdentifierMatcher(
            String equals,
            List<String> equalsAny,
            String suffix,
            List<String> suffixAny,
            List<String> aliases
    ) {
        static IdentifierMatcher empty() {
            return new IdentifierMatcher("", List.of(), "", List.of(), List.of());
        }

        public IdentifierMatcher {
            equals = normalize(equals);
            suffix = normalize(suffix);
            equalsAny = normalizeList(equalsAny);
            suffixAny = normalizeList(suffixAny);
            aliases = normalizeList(aliases);
        }

        boolean isEmpty() {
            return equals.isBlank()
                    && equalsAny.isEmpty()
                    && suffix.isBlank()
                    && suffixAny.isEmpty()
                    && aliases.isEmpty();
        }

        boolean matches(String value) {
            String normalized = normalize(value);
            if (normalized.isBlank()) {
                return false;
            }
            if (!equals.isBlank() && normalized.equals(equals)) {
                return true;
            }
            if (equalsAny.contains(normalized)) {
                return true;
            }
            if (!suffix.isBlank() && normalized.endsWith(suffix)) {
                return true;
            }
            for (String item : suffixAny) {
                if (normalized.endsWith(item)) {
                    return true;
                }
            }
            return aliases.contains(normalized) || aliases.contains(singularStem(normalized));
        }
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static String singularStem(String value) {
        String normalized = normalize(value);
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3) + "y";
        }
        if (normalized.endsWith("sses") || normalized.endsWith("xes") || normalized.endsWith("zes")
                || normalized.endsWith("ches") || normalized.endsWith("shes")) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("s") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
