package com.relationdetector.core.relation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.relationdetector.contracts.model.Endpoint;

final class NamingMatchRules {
    private NamingMatchRules() {
    }

    static Optional<Match> match(Endpoint left, Endpoint right, boolean selfJoinRole) {
        if (left == null || right == null || !left.isColumnLevel() || !right.isColumnLevel()) {
            return Optional.empty();
        }
        if (selfJoinRole && sameTable(left, right)) {
            Optional<Match> self = selfRoleId(left, right);
            if (self.isPresent()) {
                return self;
            }
        }
        Optional<Match> leftTableId = tableId(left, right);
        Optional<Match> rightTableId = tableId(right, left);
        if (leftTableId.isPresent() ^ rightTableId.isPresent()) {
            return leftTableId.isPresent() ? leftTableId : rightTableId;
        }
        Optional<Match> leftSuffix = idSuffixToId(left, right);
        Optional<Match> rightSuffix = idSuffixToId(right, left);
        if (leftSuffix.isPresent() ^ rightSuffix.isPresent()) {
            return leftSuffix.isPresent() ? leftSuffix : rightSuffix;
        }
        return Optional.empty();
    }

    private static Optional<Match> tableId(Endpoint source, Endpoint target) {
        if (!isId(target)) {
            return Optional.empty();
        }
        String sourceStem = idPrefix(source.column().columnName());
        String targetStem = singularStem(target.table().tableName());
        if (sourceStem.isBlank() || targetStem.isBlank() || sourceStem.equals(normalize(target.table().tableName()))) {
            return Optional.empty();
        }
        if (!sourceStem.equals(targetStem)) {
            return Optional.empty();
        }
        return Optional.of(match("TABLE_ID", source, target, source.column().columnName(), target.table().tableName()));
    }

    private static Optional<Match> idSuffixToId(Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return Optional.empty();
        }
        String sourceStem = singularStem(idPrefix(source.column().columnName()));
        String targetStem = singularStem(target.table().tableName());
        if (!relatedIdStem(sourceStem, targetStem)) {
            return Optional.empty();
        }
        return Optional.of(match("ID_SUFFIX_TO_ID", source, target, source.column().columnName(),
                target.table().tableName()));
    }

    private static boolean relatedIdStem(String sourceStem, String targetStem) {
        if (sourceStem.isBlank() || targetStem.isBlank()) {
            return false;
        }
        if (sourceStem.equals(targetStem)) {
            return true;
        }
        return sourceStem.endsWith("_" + targetStem) || targetStem.endsWith("_" + sourceStem);
    }

    private static Optional<Match> selfRoleId(Endpoint left, Endpoint right) {
        Optional<Match> leftToRight = selfRoleDirection(left, right);
        Optional<Match> rightToLeft = selfRoleDirection(right, left);
        if (leftToRight.isPresent() ^ rightToLeft.isPresent()) {
            return leftToRight.isPresent() ? leftToRight : rightToLeft;
        }
        return Optional.empty();
    }

    private static Optional<Match> selfRoleDirection(Endpoint source, Endpoint target) {
        if (!isId(target) || !endsWithIdSuffix(source.column().columnName())) {
            return Optional.empty();
        }
        return Optional.of(match("SELF_ROLE_ID", source, target, source.column().columnName(),
                target.table().tableName()));
    }

    private static Match match(
            String rule,
            Endpoint source,
            Endpoint target,
            String matchedColumn,
            String matchedTable
    ) {
        return new Match(rule, source, target, matchedColumn, matchedTable);
    }

    private static boolean sameTable(Endpoint left, Endpoint right) {
        return normalize(left.table().normalizedName()).equals(normalize(right.table().normalizedName()));
    }

    private static boolean isId(Endpoint endpoint) {
        return normalize(endpoint.column().columnName()).equals("id");
    }

    private static boolean endsWithIdSuffix(String column) {
        return normalize(column).endsWith("_id") && !normalize(column).equals("id");
    }

    private static String idPrefix(String column) {
        String normalized = normalize(column);
        if (!normalized.endsWith("_id") || normalized.length() <= 3) {
            return "";
        }
        return normalized.substring(0, normalized.length() - 3);
    }

    private static String singularStem(String table) {
        String value = normalize(table);
        if (value.endsWith("ies") && value.length() > 3) {
            return value.substring(0, value.length() - 3) + "y";
        }
        if (value.endsWith("sses") || value.endsWith("xes") || value.endsWith("zes")
                || value.endsWith("ches") || value.endsWith("shes")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("s") && value.length() > 3) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record Match(String rule, Endpoint source, Endpoint target, String matchedColumn, String matchedTable) {
        Map<String, Object> attributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("namingRule", rule);
            attributes.put("suggestedSourceEndpoint", source.displayName());
            attributes.put("suggestedTargetEndpoint", target.displayName());
            attributes.put("matchedColumn", matchedColumn);
            attributes.put("matchedTable", matchedTable);
            attributes.put("directionHint", true);
            return attributes;
        }
    }
}
