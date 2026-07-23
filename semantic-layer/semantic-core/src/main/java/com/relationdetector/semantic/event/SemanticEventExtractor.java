package com.relationdetector.semantic.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.SemanticFactIds;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanLineageFact;
import com.relationdetector.semantic.reader.ScanRelationshipFact;
import com.relationdetector.semantic.reader.SemanticInputPathCanonicalizer;

/**
 * CN: 按 source object 与 target table 聚合 direct VALUE write lineage，附加 touching relationships 和仅作支持的 derived lineage，生成 deterministic event candidates；不从 derived 单独创建事件。
 * EN: Groups direct VALUE write lineage by source object and target table, attaching touching relationships and supporting-only derived lineage to create deterministic event candidates. Derived lineage alone never creates an event.
 */
public final class SemanticEventExtractor {
    private final TypedSemanticEventClassifier classifier = new TypedSemanticEventClassifier();

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
            String targetTable = lineage.target().table();
            Map<String, SourceContributionGroup> contributionGroups = new LinkedHashMap<>();
            for (SourceContribution contribution : sourceContributions(lineage)) {
                String key = groupKey(contribution.source(), targetTable);
                SourceContributionGroup group = contributionGroups.computeIfAbsent(
                        key, ignored -> new SourceContributionGroup(contribution.source()));
                group.mappingKinds.add(contribution.mappingKind());
            }
            for (Map.Entry<String, SourceContributionGroup> entry : contributionGroups.entrySet()) {
                SourceContributionGroup contribution = entry.getValue();
                MutableEvent event = events.computeIfAbsent(entry.getKey(),
                        ignored -> new MutableEvent(contribution.source, classifier.eventKind(),
                                idFor(contribution.source, targetTable)));
                contribution.mappingKinds.stream()
                        .map(classifier::operationKind)
                        .forEach(event.operationKinds::add);
                String lineageRef = lineage.id();
                if (!event.lineageRefs.add(lineageRef)) {
                    continue;
                }
                event.evidenceRefs.add(lineageRef);
                event.outputEndpoints.add(lineage.target().displayName());
                for (PhysicalEndpointRef inputEndpoint : lineage.sources()) {
                    event.inputEndpoints.add(inputEndpoint.displayName());
                }
                event.confidenceSum = event.confidenceSum.add(BigDecimal.valueOf(lineage.confidence()));
                event.confidenceCount++;
                addTouchingRelationshipRefs(event, relationships);
            }
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
        return !"CONTROL".equalsIgnoreCase(lineage.flowKind());
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
            String source = entry.getValue().source().displayName();
            String target = entry.getValue().target().displayName();
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

    private List<SourceContribution> sourceContributions(ScanLineageFact lineage) {
        JsonNode document = lineage.document();
        String consensusMappingKind = document.path("attributes").path("mappingKind").asText("");
        List<SourceContribution> contributions = new ArrayList<>();
        for (JsonNode evidence : evidenceArray(document)) {
            EvidenceSource source = typedSource(evidence, lineage);
            if (source != null) {
                String mappingKind = firstNonBlank(
                        text(evidence.path("attributes"), "mappingKind"), consensusMappingKind);
                contributions.add(new SourceContribution(source, mappingKind));
            }
        }
        if (contributions.isEmpty()) {
            contributions.add(new SourceContribution(
                    new EvidenceSource("SQL_WRITE", lineage.id(), "SQL_WRITE", "", "", "", ""),
                    consensusMappingKind));
        }
        return contributions.stream()
                .distinct()
                .sorted(Comparator.comparing(SourceContribution::identityKey))
                .toList();
    }

    private EvidenceSource typedSource(JsonNode evidence, ScanLineageFact lineage) {
        String sourceObjectType = text(evidence.path("attributes"), "sourceObjectType");
        String sourceObjectName = text(evidence.path("attributes"), "sourceObjectName");
        String sourceObjectIdentity = text(evidence.path("attributes"), "sourceObjectIdentity");
        String sourceFile = text(evidence.path("attributes"), "sourceFile");
        String sourceStatementId = text(evidence.path("attributes"), "sourceStatementId");
        if (sourceObjectType.isBlank() && sourceObjectName.isBlank() && sourceFile.isBlank()
                && sourceStatementId.isBlank() && sourceObjectIdentity.isBlank()) {
            return null;
        }
        String sourceType = classifier.sourceType(sourceObjectType);
        String canonicalFile = canonicalSourceFile(sourceFile);
        String canonical = firstNonBlank(
                sourceObjectIdentity, sourceStatementId, sourceObjectName, canonicalFile, lineage.id());
        return new EvidenceSource(sourceType, canonical, classifier.sourceObjectType(sourceObjectType),
                sourceObjectName, sourceObjectIdentity, canonicalFile, sourceStatementId);
    }

    private Iterable<JsonNode> evidenceArray(JsonNode node) {
        JsonNode rawEvidence = node.path("rawEvidence");
        if (rawEvidence.isArray() && !rawEvidence.isEmpty()) {
            return rawEvidence;
        }
        JsonNode evidence = node.path("evidence");
        return evidence.isArray() ? evidence : List.of();
    }

    private String groupKey(EvidenceSource source, String targetTable) {
        if ("ROUTINE".equals(source.sourceType()) || "TRIGGER".equals(source.sourceType())) {
            return source.sourceType() + ":" + source.sourceObjectType() + ":" + source.sourceObject();
        }
        return source.sourceType() + ":" + source.sourceObject() + ":" + targetTable;
    }

    private String idFor(EvidenceSource source, String targetTable) {
        if ("ROUTINE".equals(source.sourceType())) {
            return StableSemanticId.of(
                    "event-candidate:routine",
                    source.sourceObjectType(),
                    source.sourceObject());
        }
        if ("TRIGGER".equals(source.sourceType())) {
            return "event-candidate:trigger:" + SemanticFactIds.slug(source.sourceObject());
        }
        return "event-candidate:sql-write:" + SemanticFactIds.slug(source.sourceObject()) + ":"
                + SemanticFactIds.slug(targetTable);
    }

    private String tableOf(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return PhysicalEndpointRef.column(endpoint).table();
    }

    private Set<String> lineageEndpoints(ScanLineageFact lineage) {
        Set<String> endpoints = new LinkedHashSet<>();
        lineage.sources().stream().map(PhysicalEndpointRef::displayName).forEach(endpoints::add);
        endpoints.add(lineage.target().displayName());
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

    private String canonicalSourceFile(String sourceFile) {
        if (sourceFile == null || sourceFile.isBlank()) {
            return "";
        }
        try {
            return SemanticInputPathCanonicalizer.canonicalize(Path.of(sourceFile));
        } catch (InvalidPathException ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record EvidenceSource(
            String sourceType,
            String sourceObject,
            String sourceObjectType,
            String sourceObjectName,
            String sourceObjectIdentity,
            String sourceFile,
            String sourceStatementId
    ) {
    }

    private record SourceContribution(EvidenceSource source, String mappingKind) {
        private String identityKey() {
            return source.sourceType() + "|" + source.sourceObjectType() + "|" + source.sourceObject()
                    + "|" + source.sourceObjectName() + "|" + source.sourceObjectIdentity()
                    + "|" + source.sourceFile()
                    + "|" + source.sourceStatementId() + "|" + mappingKind;
        }
    }

    private static final class SourceContributionGroup {
        private final EvidenceSource source;
        private final Set<String> mappingKinds = new LinkedHashSet<>();

        private SourceContributionGroup(EvidenceSource source) {
            this.source = source;
        }
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
