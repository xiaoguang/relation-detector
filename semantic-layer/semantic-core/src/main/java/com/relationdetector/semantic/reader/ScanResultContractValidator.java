package com.relationdetector.semantic.reader;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.DerivedPathKind;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;

/** Validates the current relation-detector JSON wire contract before typed facts are created. */
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

        validateRelationships(root.path("relationships"), "relationships");
        validateLineages(root.path("dataLineages"), "dataLineages");
        validateDerivedPaths(root.path("derivedRelationships"), "derivedRelationships", DerivedPathKind.RELATIONSHIP);
        validateDerivedPaths(root.path("derivedDataLineages"), "derivedDataLineages", DerivedPathKind.DATA_LINEAGE);
        validateNaming(root.path("namingEvidence"), false);
        validateNaming(root.path("derivedNamingEvidence"), true);
        validateWarnings(root.path("warnings"), "warnings");
        validateCounts(root, summary);
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
            require(!path.isEmpty(), at + ".path must not be empty");
            int pathIndex = 0;
            for (JsonNode endpoint : path) {
                endpoint(endpoint, at + ".path[" + pathIndex++ + "]");
            }
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
