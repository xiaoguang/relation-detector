package com.relationdetector.core.provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 *
 * Parser-neutral identity of one semantic relationship or lineage observation.
 */
public record SemanticObservationFingerprint(
        String factKind,
        String sourceEndpoint,
        String targetEndpoint,
        String factType,
        String semanticType,
        String sourceFileOrObject,
        String sourceStatementId,
        String sourceBlockId,
        long sourceLine,
        String mappingOrJoinKind,
        String conditions
) implements Comparable<SemanticObservationFingerprint> {
    public static List<SemanticObservationFingerprint> relationships(RelationshipCandidate candidate) {
        return relationships(candidate, CanonicalEndpointKeyProvider.defaults());
    }

    public static List<SemanticObservationFingerprint> relationships(
            RelationshipCandidate candidate,
            CanonicalEndpointKeyProvider endpointKeys
    ) {
        List<SemanticObservationFingerprint> result = new ArrayList<>();
        for (Evidence evidence : observations(candidate)) {
            Map<String, Object> attributes = evidence.attributes();
            result.add(new SemanticObservationFingerprint(
                    "RELATIONSHIP",
                    endpointKeys.referenceKey(candidate.source()),
                    endpointKeys.referenceKey(candidate.target()),
                    candidate.relationType() + "/" + candidate.relationSubType(),
                    evidence.type().name(),
                    sourceIdentity(evidence.source(), attributes),
                    text(attributes, "sourceStatementId"),
                    text(attributes, "sourceBlockId"),
                    number(attributes, "sourceLine"),
                    text(attributes, "joinKind"),
                    conditionIdentity(attributes)));
        }
        return result.stream().sorted().toList();
    }

    public static List<SemanticObservationFingerprint> lineages(DataLineageCandidate candidate) {
        return lineages(candidate, CanonicalEndpointKeyProvider.defaults());
    }

    public static List<SemanticObservationFingerprint> lineages(
            DataLineageCandidate candidate,
            CanonicalEndpointKeyProvider endpointKeys
    ) {
        List<SemanticObservationFingerprint> result = new ArrayList<>();
        for (DataLineageEvidence evidence : observations(candidate)) {
            Map<String, Object> attributes = evidence.attributes();
            for (Endpoint source : candidate.sources()) {
                result.add(new SemanticObservationFingerprint(
                        "DATA_LINEAGE",
                        endpointKeys.referenceKey(source),
                        endpointKeys.referenceKey(candidate.target()),
                        candidate.flowKind().name(),
                        candidate.transformType().name(),
                        sourceIdentity(evidence.source(), attributes),
                        text(attributes, "sourceStatementId"),
                        text(attributes, "sourceBlockId"),
                        number(attributes, "sourceLine"),
                        text(attributes, "mappingKind"),
                        ""));
            }
        }
        return result.stream().sorted().toList();
    }

    private static List<Evidence> observations(RelationshipCandidate candidate) {
        return candidate.rawEvidence().isEmpty() ? candidate.evidence() : candidate.rawEvidence();
    }

    private static List<DataLineageEvidence> observations(DataLineageCandidate candidate) {
        return candidate.rawEvidence().isEmpty() ? candidate.evidence() : candidate.rawEvidence();
    }

    private static String sourceIdentity(String fallback, Map<String, Object> attributes) {
        String file = text(attributes, "sourceFile");
        if (!file.isBlank()) {
            return file;
        }
        String objectType = text(attributes, "sourceObjectType");
        String objectName = text(attributes, "sourceObjectName");
        if (!objectType.isBlank() || !objectName.isBlank()) {
            return objectType + ":" + objectName;
        }
        return fallback == null ? "" : fallback;
    }

    private static String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String conditionIdentity(Map<String, Object> attributes) {
        Object value = attributes.get("conditions");
        if (!(value instanceof List<?> list)) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> condition) {
                conditions.add(String.join("|",
                        mapValue(condition, "discriminator"),
                        mapValue(condition, "operator"),
                        mapValue(condition, "value")));
            }
        }
        return conditions.stream().sorted().distinct()
                .reduce((left, right) -> left + ";" + right).orElse("");
    }

    private static String mapValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public int compareTo(SemanticObservationFingerprint other) {
        return toString().compareTo(other.toString());
    }
}
