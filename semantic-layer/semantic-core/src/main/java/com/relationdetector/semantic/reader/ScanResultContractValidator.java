package com.relationdetector.semantic.reader;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * CN: 严格校验 JsonResultWriter 当前输出的 timestamp、database、fact shape、enum、evidence 和 warning contract；允许 attributes 扩展，但未知 enum 或错置 derived shape 失败。
 * EN: Strictly validates the current JsonResultWriter contract for timestamps, database identity, fact shapes, enums, evidence, and warnings. Attribute extensions are allowed, but unknown enums or misplaced derived shapes fail.
 */
final class ScanResultContractValidator {
    private static final List<String> REQUIRED_ARRAYS = List.of(
            "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
            "namingEvidence", "derivedNamingEvidence", "warnings");

    void validate(JsonNode root) {
        require(root != null && root.isObject(), "scan result JSON root must be an object");
        JsonNode database = requireObject(root, "database");
        enumText(database, "type", DatabaseType.class);
        optionalText(database, "catalog");
        optionalText(database, "schema");
        instant(root, "generatedAt");

        JsonNode summary = requireObject(root, "summary");
        JsonNode sources = requireArray(summary, "sources");
        sources.forEach(source -> require(source.isTextual(), "summary.sources entries must be strings"));
        for (String field : REQUIRED_ARRAYS) {
            requireArray(root, field);
        }
        validateMetadataInventory(requireObject(root, "metadataInventory"));

        validateRelationships(root.path("relationships"), "relationships");
        validateLineages(root.path("dataLineages"), "dataLineages");
        validateDerivedPaths(root.path("derivedRelationships"), "derivedRelationships", DerivedPathKind.RELATIONSHIP);
        validateDerivedPaths(root.path("derivedDataLineages"), "derivedDataLineages", DerivedPathKind.DATA_LINEAGE);
        validateNaming(root.path("namingEvidence"), false);
        validateNaming(root.path("derivedNamingEvidence"), true);
        validateWarnings(root.path("warnings"), "warnings");
        validateCounts(root, summary);
    }

    void validateSectionItem(String section, JsonNode item) {
        var values = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode().add(item);
        switch (section) {
            case "relationships" -> validateRelationships(values, section);
            case "dataLineages" -> validateLineages(values, section);
            case "derivedRelationships" ->
                    validateDerivedPaths(values, section, DerivedPathKind.RELATIONSHIP);
            case "derivedDataLineages" ->
                    validateDerivedPaths(values, section, DerivedPathKind.DATA_LINEAGE);
            case "namingEvidence" -> validateNaming(values, false);
            case "derivedNamingEvidence" -> validateNaming(values, true);
            case "warnings" -> validateWarnings(values, section);
            default -> throw new ScanResultContractException("unknown scan result section: " + section);
        }
    }

    private void validateMetadataInventory(JsonNode inventory) {
        MetadataInventoryStatus status = enumText(inventory, "status", MetadataInventoryStatus.class);
        require(status == MetadataInventoryStatus.COMPLETE,
                "metadataInventory.status must be COMPLETE for semantic processing");
        JsonNode scope = requireObject(inventory, "scope");
        optionalText(scope, "catalog");
        optionalText(scope, "schema");
        textArray(requireArray(scope, "includeTables"), "metadataInventory.scope.includeTables");
        textArray(requireArray(scope, "excludeTables"), "metadataInventory.scope.excludeTables");

        JsonNode counts = requireObject(inventory, "counts");
        validateInventoryCount(inventory, counts, "tables");
        validateInventoryCount(inventory, counts, "columns");
        validateInventoryCount(inventory, counts, "constraints");
        validateInventoryCount(inventory, counts, "indexes");
        validateInventoryFacts(inventory);
    }

    /**
     * CN: 校验 COMPLETE inventory 内表、列、约束和索引的完整身份及引用闭包；输入是已验证外壳，
     * 无返回值，发现重复、悬空表或非法 ordinal 时抛出契约异常，禁止修补 inventory。
     * EN: Validates complete table, column, constraint, and index identity and reference closure in a COMPLETE
     * inventory. It returns no value and rejects duplicates, dangling tables, or invalid ordinals without repair.
     */
    private void validateInventoryFacts(JsonNode inventory) {
        Set<String> tables = new HashSet<>();
        int index = 0;
        for (JsonNode table : inventory.path("tables")) {
            String at = "metadataInventory.tables[" + index++ + "]";
            String identity = tableIdentity(table, at);
            requireText(table, "tableType", true);
            require(tables.add(identity), at + " duplicates physical table identity");
        }
        index = 0;
        Set<String> columns = new HashSet<>();
        for (JsonNode column : inventory.path("columns")) {
            String at = "metadataInventory.columns[" + index++ + "]";
            String table = tableIdentity(column, at);
            require(tables.contains(table), at + " references a table outside metadata inventory");
            String columnName = requiredText(column, "columnName");
            requireText(column, "dataType", true);
            requireText(column, "columnType", true);
            JsonNode ordinal = column.path("ordinalPosition");
            require(ordinal.isIntegralNumber() && ordinal.asInt() > 0,
                    at + ".ordinalPosition must be positive");
            require(columns.add(table + "." + columnName), at + " duplicates physical column identity");
        }
        index = 0;
        for (JsonNode constraint : inventory.path("constraints")) {
            String at = "metadataInventory.constraints[" + index++ + "]";
            require(tables.contains(tableIdentity(constraint, at)),
                    at + " references a table outside metadata inventory");
            requireText(constraint, "constraintName", true);
            requireText(constraint, "constraintType", true);
            textArray(requireArray(constraint, "columns"), at + ".columns");
            textArray(requireArray(constraint, "referencedColumns"), at + ".referencedColumns");
        }
        index = 0;
        for (JsonNode metadataIndex : inventory.path("indexes")) {
            String at = "metadataInventory.indexes[" + index++ + "]";
            require(tables.contains(tableIdentity(metadataIndex, at)),
                    at + " references a table outside metadata inventory");
            requireText(metadataIndex, "indexName", true);
            textArray(requireArray(metadataIndex, "columns"), at + ".columns");
            JsonNode ordinals = requireArray(metadataIndex, "seqInIndex");
            for (JsonNode ordinal : ordinals) {
                require(ordinal.isIntegralNumber() && ordinal.asInt() > 0,
                        at + ".seqInIndex entries must be positive integers");
            }
        }
    }

    private String tableIdentity(JsonNode value, String at) {
        optionalText(value, "catalog");
        optionalText(value, "schema");
        String table = requiredText(value, "tableName");
        String catalog = value.path("catalog").asText("");
        String schema = value.path("schema").asText("");
        return catalog.length() + ":" + catalog + "|" + schema.length() + ":" + schema + "|"
                + table.length() + ":" + table;
    }

    private String requiredText(JsonNode parent, String field) {
        requireText(parent, field, true);
        return parent.path(field).asText();
    }

    private void validateInventoryCount(JsonNode inventory, JsonNode counts, String field) {
        JsonNode values = requireArray(inventory, field);
        int expected = count(counts, field);
        require(values.size() == expected,
                "metadataInventory.counts." + field + " does not match metadata inventory array");
        int index = 0;
        for (JsonNode value : values) {
            require(value.isObject(), "metadataInventory." + field + "[" + index++ + "] must be an object");
        }
    }

    private void textArray(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            require(value.isTextual(), field + "[" + index++ + "] must be a string");
        }
    }

    private void validateRelationships(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            endpoint(value.path("source"), at + ".source");
            endpoint(value.path("target"), at + ".target");
            enumText(value, "relationType", RelationType.class);
            enumText(value, "relationSubType", RelationSubType.class);
            confidence(value, at);
            validateRelationshipEvidence(requireArray(value, "evidence"), at + ".evidence");
            validateRelationshipEvidence(requireArray(value, "rawEvidence"), at + ".rawEvidence");
            validateWarnings(requireArray(value, "warnings"), at + ".warnings");
            optionalObject(value, "attributes");
        }
    }

    private void validateLineages(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            JsonNode sources = requireArray(value, "sources");
            require(!sources.isEmpty(), at + ".sources must not be empty");
            int sourceIndex = 0;
            for (JsonNode source : sources) {
                endpoint(source, at + ".sources[" + sourceIndex++ + "]");
            }
            endpoint(value.path("target"), at + ".target");
            enumText(value, "flowKind", LineageFlowKind.class);
            enumText(value, "transformType", LineageTransformType.class);
            confidence(value, at);
            validateLineageEvidence(requireArray(value, "evidence"), at + ".evidence");
            validateLineageEvidence(requireArray(value, "rawEvidence"), at + ".rawEvidence");
            validateWarnings(requireArray(value, "warnings"), at + ".warnings");
            optionalObject(value, "attributes");
        }
    }

    private void validateDerivedPaths(JsonNode values, String field, DerivedPathKind expectedKind) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            DerivedPathKind actualKind = enumText(value, "kind", DerivedPathKind.class);
            require(actualKind == expectedKind, at + ".kind must be " + expectedKind);
            endpoint(value.path("source"), at + ".source");
            endpoint(value.path("target"), at + ".target");
            JsonNode pathLength = value.path("pathLength");
            require(pathLength.isIntegralNumber() && pathLength.asInt() > 0,
                    at + ".pathLength must be a positive integer");
            JsonNode path = requireArray(value, "path");
            require(path.size() >= 3, at + ".path must contain at least three endpoints");
            require(pathLength.asInt() == path.size() - 1,
                    at + ".pathLength must equal path.size - 1");
            int pathIndex = 0;
            for (JsonNode endpoint : path) {
                endpoint(endpoint, at + ".path[" + pathIndex++ + "]");
            }
            require(value.path("source").equals(path.get(0)),
                    at + ".source must equal the first path endpoint");
            require(value.path("target").equals(path.get(path.size() - 1)),
                    at + ".target must equal the last path endpoint");
            confidence(value, at);
            validateRelationshipEvidence(requireArray(value, "evidence"), at + ".evidence");
            validateRelationshipEvidence(requireArray(value, "rawEvidence"), at + ".rawEvidence");
            optionalObject(value, "attributes");
        }
    }

    private void validateNaming(JsonNode values, boolean lightweight) {
        int index = 0;
        for (JsonNode value : values) {
            String at = (lightweight ? "derivedNamingEvidence" : "namingEvidence") + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            requireText(value, "id", true);
            endpoint(value.path("source"), at + ".source");
            endpoint(value.path("target"), at + ".target");
            requireText(value, "rule", true);
            require(value.path("directionHint").isBoolean(), at + ".directionHint must be a boolean");
            if (!lightweight) {
                validateRelationshipEvidence(requireArray(value, "evidence"), at + ".evidence");
                validateRelationshipEvidence(requireArray(value, "rawEvidence"), at + ".rawEvidence");
            }
        }
    }

    private void validateRelationshipEvidence(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            enumText(value, "type", EvidenceType.class);
            commonEvidence(value, at);
        }
    }

    private void validateLineageEvidence(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            require("DATA_LINEAGE".equals(value.path("type").asText()), at + ".type must be DATA_LINEAGE");
            enumText(value, "transformType", LineageTransformType.class);
            commonEvidence(value, at);
        }
    }

    private void commonEvidence(JsonNode value, String at) {
        enumText(value, "sourceType", EvidenceSourceType.class);
        require(value.path("score").isNumber(), at + ".score must be a number");
        requireText(value, "source", false);
        requireText(value, "detail", false);
        requireObject(value, "attributes");
    }

    private void validateWarnings(JsonNode values, String field) {
        int index = 0;
        for (JsonNode value : values) {
            String at = field + "[" + index++ + "]";
            require(value.isObject(), at + " must be an object");
            enumText(value, "type", WarningType.class);
            enumText(value, "severity", WarningSeverity.class);
            requireText(value, "code", false);
            requireText(value, "message", false);
            requireText(value, "source", false);
            require(value.path("line").isIntegralNumber(), at + ".line must be an integer");
            requireObject(value, "attributes");
        }
    }

    private void endpoint(JsonNode endpoint, String field) {
        require(endpoint != null && endpoint.isObject(), field + " must be an object");
        requireText(endpoint, "table", true);
        optionalText(endpoint, "column");
    }

    private void confidence(JsonNode value, String field) {
        JsonNode confidence = value.path("confidence");
        require(confidence.isNumber(), field + ".confidence must be a number");
        double number = confidence.asDouble();
        require(Double.isFinite(number) && number >= 0.0d && number <= 1.0d,
                field + ".confidence must be within [0,1]");
    }

    private void validateCounts(JsonNode root, JsonNode summary) {
        int directRelationships = count(summary, "directRelationshipCount");
        int derivedRelationships = count(summary, "derivedRelationshipCount");
        int directLineages = count(summary, "directDataLineageCount");
        int derivedLineages = count(summary, "derivedDataLineageCount");
        int directNaming = count(summary, "directNamingEvidenceCount");
        int derivedNaming = count(summary, "derivedNamingEvidenceCount");
        equal(directRelationships, root.path("relationships").size(), "directRelationshipCount");
        equal(derivedRelationships, root.path("derivedRelationships").size(), "derivedRelationshipCount");
        equal(count(summary, "totalRelationshipCount"), directRelationships + derivedRelationships,
                "totalRelationshipCount");
        equal(directLineages, root.path("dataLineages").size(), "directDataLineageCount");
        equal(derivedLineages, root.path("derivedDataLineages").size(), "derivedDataLineageCount");
        equal(count(summary, "totalDataLineageCount"), directLineages + derivedLineages,
                "totalDataLineageCount");
        equal(count(summary, "totalNamingEvidenceCount"), directNaming + derivedNaming,
                "totalNamingEvidenceCount");
        equal(count(summary, "totalNamingEvidenceCount"), root.path("namingEvidence").size(),
                "namingEvidence size");
        equal(derivedNaming, root.path("derivedNamingEvidence").size(), "derivedNamingEvidenceCount");
        equal(count(summary, "warningCount"), root.path("warnings").size(), "warningCount");
    }

    private int count(JsonNode summary, String field) {
        JsonNode value = summary.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0,
                "summary." + field + " must be a non-negative integer");
        return value.asInt();
    }

    private void instant(JsonNode parent, String field) {
        requireText(parent, field, true);
        try {
            Instant.parse(parent.path(field).asText());
        } catch (DateTimeParseException ex) {
            throw new ScanResultContractException(field + " must be an ISO-8601 instant");
        }
    }

    private <E extends Enum<E>> E enumText(JsonNode parent, String field, Class<E> type) {
        requireText(parent, field, true);
        String value = parent.path(field).asText().trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new ScanResultContractException(field + " contains unknown " + type.getSimpleName() + ": " + value);
        }
    }

    private JsonNode requireObject(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        require(value.isObject(), field + " must be an object");
        return value;
    }

    private JsonNode requireArray(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        require(value.isArray(), field + " must be an array");
        return value;
    }

    private void optionalObject(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        require(value == null || value.isNull() || value.isObject(), field + " must be an object when present");
    }

    private void requireText(JsonNode parent, String field, boolean nonBlank) {
        JsonNode value = parent.path(field);
        require(value.isTextual(), field + " must be a string");
        require(!nonBlank || !value.asText().isBlank(), field + " must not be blank");
    }

    private void optionalText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        require(value == null || value.isNull() || value.isTextual(), field + " must be a string when present");
    }

    private void equal(int actual, int expected, String field) {
        require(actual == expected, "summary " + field + " does not match fact arrays");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ScanResultContractException(message);
        }
    }
}
