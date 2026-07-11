package com.relationdetector.semantic.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;

/** Extracts deterministic event candidates from write lineage and supporting evidence. */
public final class SemanticEventExtractor {

    public List<SemanticEventCandidate> extract(ScanBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        Map<String, MutableEvent> events = new LinkedHashMap<>();
        Map<String, ScanRelationshipFact> relationships = relationshipIndex(bundle);
        addDirectLineages(events, bundle.dataLineages(), relationships);
        addSupportingDerivedLineages(events, bundle.derivedDataLineages());
        return events.values().stream().map(MutableEvent::toCandidate).toList();
    }

    private void addDirectLineages(
            Map<String, MutableEvent> events,
            List<ScanLineageFact> lineages,
            Map<String, ScanRelationshipFact> relationships
    ) {
        for (ScanLineageFact lineage : lineages) {
            if (!isWriteValueLineage(lineage)) {
                continue;
            }
            String target = lineage.target();
            if (target.isBlank()) {
                continue;
            }
            JsonNode document = lineage.document();
            EvidenceSource source = sourceOf(document);
            String targetTable = tableOf(target);
            String groupKey = groupKey(source, targetTable);
            MutableEvent event = events.computeIfAbsent(groupKey,
                    ignored -> new MutableEvent(source, eventKind(document, targetTable), idFor(source, targetTable)));
            String lineageRef = lineage.id();
            event.lineageRefs.add(lineageRef);
            event.evidenceRefs.add(lineageRef);
            event.operationKinds.add(operationKind(document));
            event.outputEndpoints.add(target);
            for (String sourceName : lineage.sources()) {
                event.inputEndpoints.add(sourceName);
            }
            event.confidenceSum = event.confidenceSum.add(BigDecimal.valueOf(lineage.confidence()));
            event.confidenceCount++;
            addTouchingRelationshipRefs(event, relationships);
        }
    }

    private void addSupportingDerivedLineages(Map<String, MutableEvent> events, List<ScanLineageFact> lineages) {
        for (ScanLineageFact lineage : lineages) {
            if (!isWriteValueLineage(lineage)) {
                continue;
            }
            String ref = lineage.id();
            Set<String> endpoints = lineageEndpoints(lineage);
            for (MutableEvent event : events.values()) {
                if (touchesAny(event, endpoints)) {
                    event.supportingDerivedLineageRefs.add(ref);
                    event.evidenceRefs.add(ref);
                }
            }
        }
    }

    private boolean isWriteValueLineage(ScanLineageFact lineage) {
        return !"CONTROL".equalsIgnoreCase(lineage.flowKind()) && !lineage.target().isBlank();
    }

    private Map<String, ScanRelationshipFact> relationshipIndex(ScanBundle bundle) {
        Map<String, ScanRelationshipFact> result = new LinkedHashMap<>();
        for (ScanRelationshipFact relationship : bundle.relationships()) {
            result.put(relationship.id(), relationship);
        }
        return result;
    }

    private void addTouchingRelationshipRefs(
            MutableEvent event,
            Map<String, ScanRelationshipFact> relationships
    ) {
        for (Map.Entry<String, ScanRelationshipFact> entry : relationships.entrySet()) {
            String source = entry.getValue().source();
            String target = entry.getValue().target();
            if (relationshipTouchesEvent(source, target, event)) {
                event.relationshipRefs.add(entry.getKey());
                event.evidenceRefs.add(entry.getKey());
            }
        }
    }

    private boolean relationshipTouchesEvent(String source, String target, MutableEvent event) {
        if (event.outputEndpoints.contains(source) || event.outputEndpoints.contains(target)) {
            return true;
        }
        String sourceTable = tableOf(source);
        String targetTable = tableOf(target);
        Set<String> inputTables = tablesOf(event.inputEndpoints);
        Set<String> outputTables = tablesOf(event.outputEndpoints);
        return (outputTables.contains(sourceTable) && inputTables.contains(targetTable))
                || (outputTables.contains(targetTable) && inputTables.contains(sourceTable));
    }

    private Set<String> tablesOf(Set<String> endpoints) {
        Set<String> tables = new LinkedHashSet<>();
        for (String endpoint : endpoints) {
            String table = tableOf(endpoint);
            if (!table.isBlank()) {
                tables.add(table);
            }
        }
        return tables;
    }

    private EvidenceSource sourceOf(JsonNode lineage) {
        for (JsonNode evidence : evidenceArray(lineage)) {
            String source = evidence.path("source").asText("");
            String sourceObjectType = text(evidence.path("attributes"), "sourceObjectType");
            String sourceObjectName = text(evidence.path("attributes"), "sourceObjectName");
            String sourceFile = text(evidence.path("attributes"), "sourceFile");
            String sourceStatementId = text(evidence.path("attributes"), "sourceStatementId");
            String sourceBlockId = text(evidence.path("attributes"), "sourceBlockId");
            if (sourceStatementId.isBlank()) {
                sourceStatementId = sourceBlockId;
            }
            if (!source.isBlank() || !sourceObjectName.isBlank() || !sourceFile.isBlank()) {
                String sourceType = sourceType(source, sourceObjectType, sourceFile);
                String objectName = !sourceObjectName.isBlank() ? sourceObjectName : objectNameFromSource(source);
                String canonical = !objectName.isBlank() ? objectName : canonicalSourceFile(sourceFile, source);
                return new EvidenceSource(sourceType, canonical, sourceType, objectName, canonicalSourceFile(sourceFile, ""),
                        sourceStatementId);
            }
        }
        return new EvidenceSource("SQL_WRITE", "lineage:" + endpoint(lineage.path("target")),
                "SQL_WRITE", "", "", "");
    }

    private Iterable<JsonNode> evidenceArray(JsonNode node) {
        JsonNode rawEvidence = node.path("rawEvidence");
        if (rawEvidence.isArray() && !rawEvidence.isEmpty()) {
            return rawEvidence;
        }
        JsonNode evidence = node.path("evidence");
        return evidence.isArray() ? evidence : List.of();
    }

    private String sourceType(String source, String sourceObjectType, String sourceFile) {
        if (!sourceObjectType.isBlank()) {
            String upperType = sourceObjectType.toUpperCase(Locale.ROOT);
            if ("ROUTINE".equals(upperType) || "PROCEDURE".equals(upperType) || "FUNCTION".equals(upperType)
                    || "PACKAGE".equals(upperType) || "PACKAGE_BODY".equals(upperType)) {
                return "ROUTINE";
            }
            if ("TRIGGER".equals(upperType)) {
                return "TRIGGER";
            }
            if ("DATA_LOAD".equals(upperType) || "QUERY".equals(upperType) || "SQL_WRITE".equals(upperType)) {
                return upperType;
            }
        }
        String upper = source.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROUTINE:")) {
            return "ROUTINE";
        }
        if (upper.startsWith("TRIGGER:")) {
            return "TRIGGER";
        }
        String lowerFile = sourceFile.toLowerCase(Locale.ROOT);
        if (lowerFile.contains("/02-procedures/") || lowerFile.contains("\\02-procedures\\")) {
            return "ROUTINE";
        }
        if (lowerFile.contains("trigger")) {
            return "TRIGGER";
        }
        if (lowerFile.contains("/03-data/") || lowerFile.contains("\\03-data\\")) {
            return "DATA_LOAD";
        }
        if (lowerFile.contains("/04-queries/") || lowerFile.contains("\\04-queries\\")) {
            return "QUERY";
        }
        return "SQL_WRITE";
    }

    private String groupKey(EvidenceSource source, String targetTable) {
        if ("ROUTINE".equals(source.sourceType()) || "TRIGGER".equals(source.sourceType())) {
            return source.sourceType() + ":" + source.sourceObject();
        }
        return source.sourceType() + ":" + source.sourceObject() + ":" + targetTable;
    }

    private String idFor(EvidenceSource source, String targetTable) {
        if ("ROUTINE".equals(source.sourceType()) || "TRIGGER".equals(source.sourceType())) {
            return "event-candidate:" + source.sourceType().toLowerCase(Locale.ROOT) + ":"
                    + SemanticFactIds.slug(source.sourceObject());
        }
        return "event-candidate:sql-write:" + SemanticFactIds.slug(source.sourceObject()) + ":"
                + SemanticFactIds.slug(targetTable);
    }

    private String eventKind(JsonNode lineage, String targetTable) {
        String target = targetTable.toLowerCase(Locale.ROOT);
        String targetColumn = lineage.path("target").path("column").asText("").toLowerCase(Locale.ROOT);
        if (target.endsWith("_fact") || target.contains("_fact_") || target.contains("fact")) {
            return "FACT_REFRESH";
        }
        if (target.endsWith("_dim") || target.contains("_dim_") || target.contains("dimension")) {
            return "DIMENSION_REFRESH";
        }
        if (target.contains("inventory") || target.contains("stock")) {
            return "INVENTORY_MOVEMENT";
        }
        if (target.contains("cashier") || target.contains("reconciliation")) {
            return "CASH_RECONCILIATION";
        }
        if (target.contains("account") || target.contains("payment") || target.contains("voucher")) {
            return "ACCOUNTING_POSTING";
        }
        if (targetColumn.contains("status") || targetColumn.contains("approved") || targetColumn.contains("closed")) {
            return "STATE_CHANGE";
        }
        return "SQL_WRITE_OPERATION";
    }

    private String operationKind(JsonNode lineage) {
        String mappingKind = lineage.path("attributes").path("mappingKind").asText("");
        if (!mappingKind.isBlank()) {
            return mappingKind;
        }
        String detail = "";
        for (JsonNode evidence : evidenceArray(lineage)) {
            detail = evidence.path("detail").asText("");
            if (!detail.isBlank()) {
                break;
            }
        }
        String upper = detail.toUpperCase(Locale.ROOT);
        if (upper.contains("MERGE")) {
            return "MERGE";
        }
        if (upper.contains("UPDATE")) {
            return "UPDATE";
        }
        if (upper.contains("DELETE")) {
            return "DELETE";
        }
        if (upper.contains("INSERT")) {
            return "INSERT";
        }
        return lineage.path("transformType").asText("WRITE");
    }

    private String endpoint(JsonNode endpoint) {
        return SemanticFactIds.endpoint(endpoint);
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        int index = endpoint.lastIndexOf('.');
        return index < 0 ? endpoint : endpoint.substring(0, index);
    }

    private Set<String> lineageEndpoints(ScanLineageFact lineage) {
        Set<String> endpoints = new LinkedHashSet<>(lineage.sources());
        String target = lineage.target();
        if (!target.isBlank()) {
            endpoints.add(target);
        }
        return endpoints;
    }

    private boolean touchesAny(MutableEvent event, Set<String> endpoints) {
        for (String endpoint : endpoints) {
            if (event.inputEndpoints.contains(endpoint) || event.outputEndpoints.contains(endpoint)) {
                return true;
            }
            String table = tableOf(endpoint);
            if (event.inputEndpoints.stream().map(this::tableOf).anyMatch(table::equals)
                    || event.outputEndpoints.stream().map(this::tableOf).anyMatch(table::equals)) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String field) {
        return node == null || !node.isObject() ? "" : node.path(field).asText("");
    }

    private String objectNameFromSource(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String value = source;
        if (value.regionMatches(true, 0, "ROUTINE:", 0, "ROUTINE:".length())) {
            value = value.substring("ROUTINE:".length());
        } else if (value.regionMatches(true, 0, "TRIGGER:", 0, "TRIGGER:".length())) {
            value = value.substring("TRIGGER:".length());
        }
        return value.contains("/") || value.contains("\\") ? "" : value;
    }

    private String canonicalSourceFile(String sourceFile, String fallback) {
        String value = !sourceFile.isBlank() ? sourceFile : fallback;
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\\', '/').replaceFirst("^.*/(relation-detector|semantic-layer)/", "$1/");
    }

    private record EvidenceSource(
            String sourceType,
            String sourceObject,
            String sourceObjectType,
            String sourceObjectName,
            String sourceFile,
            String sourceStatementId
    ) {
    }

    private static final class MutableEvent {
        private final EvidenceSource source;
        private final String eventKind;
        private final String id;
        private final Set<String> operationKinds = new LinkedHashSet<>();
        private final Set<String> inputEndpoints = new LinkedHashSet<>();
        private final Set<String> outputEndpoints = new LinkedHashSet<>();
        private final Set<String> lineageRefs = new LinkedHashSet<>();
        private final Set<String> supportingDerivedLineageRefs = new LinkedHashSet<>();
        private final Set<String> relationshipRefs = new LinkedHashSet<>();
        private final Set<String> evidenceRefs = new LinkedHashSet<>();
        private BigDecimal confidenceSum = BigDecimal.ZERO;
        private int confidenceCount;

        private MutableEvent(EvidenceSource source, String eventKind, String id) {
            this.source = source;
            this.eventKind = eventKind;
            this.id = id;
        }

        private SemanticEventCandidate toCandidate() {
            BigDecimal confidence = confidenceCount == 0 ? BigDecimal.ZERO
                    : confidenceSum.divide(BigDecimal.valueOf(confidenceCount), 4, RoundingMode.HALF_UP);
            EventReadableNameSuggester.EventNameSuggestion suggestion = new EventReadableNameSuggester().suggest(
                    eventKind, source.sourceObject(), inputEndpoints, outputEndpoints);
            return new SemanticEventCandidate(id, eventKind, source.sourceType(), source.sourceObject(),
                    source.sourceObjectType(), source.sourceObjectName(), source.sourceFile(), source.sourceStatementId(),
                    suggestion.readableNameHint(), suggestion.businessActionHint(), suggestion.eventNameBasis(),
                    List.copyOf(operationKinds), List.copyOf(inputEndpoints), List.copyOf(outputEndpoints),
                    List.copyOf(lineageRefs), List.copyOf(supportingDerivedLineageRefs),
                    List.copyOf(relationshipRefs), List.copyOf(evidenceRefs), confidence,
                    Map.of("directLineageCount", lineageRefs.size(),
                            "supportingDerivedLineageCount", supportingDerivedLineageRefs.size()));
        }
    }
}
