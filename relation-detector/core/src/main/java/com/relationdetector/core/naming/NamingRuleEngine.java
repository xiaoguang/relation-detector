package com.relationdetector.core.naming;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 * CN: 对一对有向 column endpoints 执行已配置的系统和用户 naming rules，返回可审计的匹配结果；
 * 不读取 SQL 文本，不创建 relationship，也不跨 catalog 猜测表身份。
 * EN: Evaluates configured naming rules for one directional column-endpoint pair and returns auditable matches;
 * it does not read SQL text, create relationships, or guess table identity across catalogs.
 */
public final class NamingRuleEngine {
    private final CanonicalEndpointKeyProvider endpointKeys;

    public NamingRuleEngine() {
        this(CanonicalEndpointKeyProvider.defaults());
    }

    NamingRuleEngine(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = java.util.Objects.requireNonNull(endpointKeys, "endpointKeys");
    }
    public List<Match> match(
            Endpoint left,
            Endpoint right,
            NamingRuleScope scope,
            boolean selfJoinRole,
            NamingRuleSet ruleSet
    ) {
        if (left == null || right == null || !left.isColumnLevel() || !right.isColumnLevel()) {
            return List.of();
        }
        List<Match> matches = new ArrayList<>();
        for (NamingRule rule : ruleSet.rules()) {
            if (!rule.appliesTo(scope) || (rule.requireSelfJoinRole() && !selfJoinRole)) {
                continue;
            }
            if ("USER_CONFIGURED".equals(rule.rule())) {
                userConfigured(rule, left, right).ifPresent(matches::add);
                continue;
            }
            Optional<Match> builtIn = builtIn(rule, left, right, selfJoinRole);
            if (builtIn.isPresent()) {
                matches.add(builtIn.get());
                return List.copyOf(matches);
            }
        }
        return List.copyOf(matches);
    }

    private Optional<Match> builtIn(NamingRule rule, Endpoint left, Endpoint right, boolean selfJoinRole) {
        if ("SELF_ROLE_ID".equals(rule.rule()) && selfJoinRole && sameTable(left, right)) {
            return unambiguous(rule, selfRoleDirection(rule, left, right), selfRoleDirection(rule, right, left));
        }
        if ("TABLE_ID".equals(rule.rule())) {
            return unambiguous(rule, tableId(rule, left, right), tableId(rule, right, left));
        }
        if ("ID_SUFFIX_TO_ID".equals(rule.rule())) {
            return unambiguous(rule, idSuffixToId(rule, left, right), idSuffixToId(rule, right, left));
        }
        return Optional.empty();
    }

    private Optional<Match> unambiguous(
            NamingRule rule,
            Optional<Match> leftToRight,
            Optional<Match> rightToLeft
    ) {
        if (leftToRight.isPresent() ^ rightToLeft.isPresent()) {
            return leftToRight.isPresent() ? leftToRight : rightToLeft;
        }
        return Optional.empty();
    }

    private Optional<Match> tableId(NamingRule rule, Endpoint source, Endpoint target) {
        if (!isId(target)) {
            return Optional.empty();
        }
        String sourceStem = idPrefix(source.column().columnName());
        String targetStem = NamingRule.singularStem(target.table().tableName());
        if (sourceStem.isBlank() || targetStem.isBlank()
                || sourceStem.equals(NamingRule.normalize(target.table().tableName()))) {
            return Optional.empty();
        }
        if (!sourceStem.equals(targetStem)) {
            return Optional.empty();
        }
        return Optional.of(match(rule, source, target, source.column().columnName(), target.table().tableName()));
    }

    private Optional<Match> idSuffixToId(NamingRule rule, Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return Optional.empty();
        }
        String sourceStem = NamingRule.singularStem(idPrefix(source.column().columnName()));
        String targetStem = NamingRule.singularStem(target.table().tableName());
        if (!relatedIdStem(sourceStem, targetStem)) {
            return Optional.empty();
        }
        return Optional.of(match(rule, source, target, source.column().columnName(), target.table().tableName()));
    }

    private Optional<Match> selfRoleDirection(NamingRule rule, Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return Optional.empty();
        }
        return Optional.of(match(rule, source, target, source.column().columnName(), target.table().tableName()));
    }

    private Optional<Match> userConfigured(NamingRule rule, Endpoint left, Endpoint right) {
        if (matchesConfigured(rule.sourceMatcher(), left) && matchesConfigured(rule.targetMatcher(), right)) {
            return Optional.of(match(rule, left, right, left.column().columnName(), right.table().tableName()));
        }
        if (matchesConfigured(rule.sourceMatcher(), right) && matchesConfigured(rule.targetMatcher(), left)) {
            return Optional.of(match(rule, right, left, right.column().columnName(), left.table().tableName()));
        }
        return Optional.empty();
    }

    private boolean matchesConfigured(NamingRule.EndpointMatcher matcher, Endpoint endpoint) {
        if (matcher == null || endpoint == null || !endpoint.isColumnLevel()) {
            return false;
        }
        if (!matcher.endpoint().isBlank()) {
            return matcher.endpoint().equals(endpoint.normalizedKey());
        }
        boolean hasAnyMatcher = false;
        if (!matcher.table().isEmpty()) {
            hasAnyMatcher = true;
            if (!matcher.table().matches(endpoint.table().tableName())) {
                return false;
            }
        }
        if (!matcher.column().isEmpty()) {
            hasAnyMatcher = true;
            if (!matcher.column().matches(endpoint.column().columnName())) {
                return false;
            }
        }
        return hasAnyMatcher;
    }

    private Match match(
            NamingRule rule,
            Endpoint source,
            Endpoint target,
            String matchedColumn,
            String matchedTable
    ) {
        return new Match(rule.rule(), rule.id(), rule.description(), rule.ruleSource(),
                source, target, matchedColumn, matchedTable, rule.directionHint());
    }

    private boolean relatedIdStem(String sourceStem, String targetStem) {
        if (sourceStem.isBlank() || targetStem.isBlank()) {
            return false;
        }
        if (sourceStem.equals(targetStem)) {
            return true;
        }
        return sourceStem.endsWith("_" + targetStem) || targetStem.endsWith("_" + sourceStem);
    }

    private boolean sameTable(Endpoint left, Endpoint right) {
        return endpointKeys.sameTable(left.table(), right.table());
    }

    private boolean isId(Endpoint endpoint) {
        return NamingRule.normalize(endpoint.column().columnName()).equals("id");
    }

    private boolean endsWithIdSuffix(String column) {
        String normalized = NamingRule.normalize(column);
        return normalized.endsWith("_id") && !normalized.equals("id");
    }

    private String idPrefix(String column) {
        String normalized = NamingRule.normalize(column);
        if (!normalized.endsWith("_id") || normalized.length() <= 3) {
            return "";
        }
        return normalized.substring(0, normalized.length() - 3);
    }

    public record Match(
            String rule,
            String ruleId,
            String description,
            String ruleSource,
            Endpoint source,
            Endpoint target,
            String matchedColumn,
            String matchedTable,
            boolean directionHint
    ) {
        Map<String, Object> attributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("namingRule", rule);
            attributes.put("suggestedSourceEndpoint", source.displayName());
            attributes.put("suggestedTargetEndpoint", target.displayName());
            attributes.put("suggestedSourceEndpointKey", source.normalizedKey());
            attributes.put("suggestedTargetEndpointKey", target.normalizedKey());
            attributes.put("matchedColumn", matchedColumn);
            attributes.put("matchedTable", matchedTable);
            attributes.put("directionHint", directionHint);
            attributes.put("configuredRuleId", ruleId);
            attributes.put("ruleSource", ruleSource);
            if (!description.isBlank()) {
                attributes.put("configuredRuleDescription", description);
            }
            return attributes;
        }
    }
}
