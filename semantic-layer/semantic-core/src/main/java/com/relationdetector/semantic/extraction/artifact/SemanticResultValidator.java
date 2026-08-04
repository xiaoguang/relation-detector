package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.artifact.SemanticResultSelection;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

/**
 * CN: 对完成选择后的 path-backed semantic sections 执行 evidence、owned grounding、typed candidate、
 * 完整物理标识和跨 section 闭包校验。输入是外排 section stores 与完整 evidence store，成功无输出，
 * 失败原子拒绝最终发布；本类不合并 variant、不应用 reconciliation patch，也不写最终文档。
 * EN: Revalidates evidence, owned grounding, typed candidates, complete physical identities, and cross-section closure
 * over selected path-backed semantic sections. Failure atomically rejects publication; this class neither merges
 * variants, applies reconciliation patches, nor renders artifacts.
 */
public final class SemanticResultValidator {
    static final List<String> ENTITY_REF_FIELDS = List.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef");
    private static final List<SemanticEvidenceStore.Section> GROUNDING_SECTIONS = List.of(
            SemanticEvidenceStore.Section.METADATA_TABLES,
            SemanticEvidenceStore.Section.METADATA_COLUMNS,
            SemanticEvidenceStore.Section.METADATA_CONSTRAINTS,
            SemanticEvidenceStore.Section.METADATA_INDEXES,
            SemanticEvidenceStore.Section.RELATIONSHIPS,
            SemanticEvidenceStore.Section.LINEAGE,
            SemanticEvidenceStore.Section.EVENT_CANDIDATES,
            SemanticEvidenceStore.Section.DERIVED_RELATIONSHIPS,
            SemanticEvidenceStore.Section.DERIVED_LINEAGE,
            SemanticEvidenceStore.Section.NAMING_EVIDENCE,
            SemanticEvidenceStore.Section.REVIEW_ITEM_CANDIDATES,
            SemanticEvidenceStore.Section.TRIPLET_CANDIDATES,
            SemanticEvidenceStore.Section.DIAGNOSTICS);
    private final SemanticEvidenceLookup evidenceLookup;
    private final Map<SemanticResultStore.Section, ExternalJsonRecordStore> sections;
    private final SemanticResultSelection selection;

    public SemanticResultValidator(
            SemanticEvidenceLookup evidenceLookup,
            Map<SemanticResultStore.Section, ExternalJsonRecordStore> sections,
            SemanticResultSelection selection
    ) {
        this.evidenceLookup = evidenceLookup;
        this.sections = sections;
        this.selection = selection;
    }

    public void requireEvidence(
            SemanticResultStore.Section section,
            String id,
            JsonNode evidenceRefs
    ) {
        if (!evidenceRefs.isArray() || evidenceRefs.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic item requires evidenceRefs: " + section.wireName + ":" + id);
        }
        for (JsonNode value : evidenceRefs) {
            String reference = value.isTextual() ? value.asText() : "";
            if (reference.isBlank() || !evidenceLookup.containsReference(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic item contains unresolved evidence reference");
            }
        }
    }

    public void validate() {
        validateSemanticReferences();
    }

    private void validateSemanticReferences() {
        for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
            sections.get(section).forEach(record ->
                    validateSelectedItem(
                            section,
                            record.key(),
                            selection.selectedDocument(section, record.value())));
        }
    }

    private void validateSelectedItem(
            SemanticResultStore.Section section,
            String storedId,
            JsonNode item
    ) {
        if (item == null || !item.isObject()) {
            throw new SemanticExtractionValidationException(
                    "selected semantic item must be an object");
        }
        String id = item.path("id").asText("");
        if (id.isBlank() || !id.equals(storedId)) {
            throw new SemanticExtractionValidationException(
                    "selected semantic item identity does not match its stored identity");
        }
        requireEvidence(section, id, item.path("evidenceRefs"));
        requireOwnedGrounding(section, id, item.path("ownedGroundingRefs"));
        validateCandidateReference(section, item);
        validatePhysicalReferences(section, id, item);
        validateEntityReferences(section, item);
        if (section == SemanticResultStore.Section.REVIEW_ITEMS) {
            validateReviewTarget(section, item);
        }
    }

    private void requireOwnedGrounding(
            SemanticResultStore.Section section,
            String id,
            JsonNode groundingRefs
    ) {
        if (!groundingRefs.isArray() || groundingRefs.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "semantic item requires ownedGroundingRefs: " + section.wireName + ":" + id);
        }
        for (JsonNode value : groundingRefs) {
            String reference = value.isTextual() ? value.asText() : "";
            if (reference.isBlank() || !isFactOrCandidate(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic item contains a non-fact grounding reference");
            }
        }
    }

    private boolean isFactOrCandidate(String reference) {
        for (SemanticEvidenceStore.Section section : GROUNDING_SECTIONS) {
            if (evidenceLookup.containsReference(section, reference)) {
                return true;
            }
        }
        return false;
    }

    private void validateCandidateReference(
            SemanticResultStore.Section section,
            JsonNode item
    ) {
        if (section == SemanticResultStore.Section.EVENTS) {
            requireTypedCandidate(
                    item.path("eventCandidateRef"),
                    SemanticEvidenceStore.Section.EVENT_CANDIDATES,
                    "semantic event contains an invalid eventCandidateRef");
        } else if (section == SemanticResultStore.Section.TRIPLETS) {
            requireTypedCandidate(
                    item.path("candidateRef"),
                    SemanticEvidenceStore.Section.TRIPLET_CANDIDATES,
                    "semantic triplet contains an invalid candidateRef");
        }
    }

    private void requireTypedCandidate(
            JsonNode value,
            SemanticEvidenceStore.Section expected,
            String message
    ) {
        String reference = value.isTextual() ? value.asText() : "";
        if (reference.isBlank() || !evidenceLookup.containsReference(expected, reference)) {
            throw new SemanticExtractionValidationException(message);
        }
    }

    private void validatePhysicalReferences(
            SemanticResultStore.Section section,
            String id,
            JsonNode item
    ) {
        switch (section) {
            case ENTITIES -> requirePhysicalTable(section, id, "physicalName", item.path("physicalName"));
            case LINEAGE -> {
                requirePhysicalColumns(section, id, "fromPhysical", item.path("fromPhysical"));
                requirePhysicalColumn(section, id, "toPhysical", item.path("toPhysical"));
            }
            case METRICS -> {
                requirePhysicalColumn(section, id, "physicalField", item.path("physicalField"));
                requirePhysicalColumns(section, id, "sourceFields", item.path("sourceFields"));
            }
            case DIMENSIONS -> {
                requirePhysicalColumn(section, id, "physicalField", item.path("physicalField"));
                requirePhysicalTable(section, id, "dimensionTable", item.path("dimensionTable"));
            }
            default -> {
                // This semantic section contains no typed physical table or column field.
            }
        }
    }

    private void requirePhysicalTable(
            SemanticResultStore.Section section,
            String id,
            String field,
            JsonNode value
    ) {
        if (absent(value)) {
            return;
        }
        if (!value.isTextual()
                || !evidenceLookup.containsPhysicalTable(value.asText())) {
            throw unknownPhysical(section, id, field, "table");
        }
    }

    private void requirePhysicalColumn(
            SemanticResultStore.Section section,
            String id,
            String field,
            JsonNode value
    ) {
        if (absent(value)) {
            return;
        }
        if (!value.isTextual()
                || !evidenceLookup.containsPhysicalColumn(value.asText())) {
            throw unknownPhysical(section, id, field, "column");
        }
    }

    private void requirePhysicalColumns(
            SemanticResultStore.Section section,
            String id,
            String field,
            JsonNode values
    ) {
        if (absent(values)) {
            return;
        }
        if (!values.isArray()) {
            throw unknownPhysical(section, id, field, "column");
        }
        for (JsonNode value : values) {
            if (!value.isTextual()
                    || value.asText().isBlank()
                    || !evidenceLookup.containsPhysicalColumn(value.asText())) {
                throw unknownPhysical(section, id, field, "column");
            }
        }
    }

    private SemanticExtractionValidationException unknownPhysical(
            SemanticResultStore.Section section,
            String id,
            String field,
            String kind
    ) {
        return new SemanticExtractionValidationException(
                section.wireName + ":" + id + " contains an unknown physical " + kind + " in " + field);
    }

    private boolean absent(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull()
                || value.isTextual() && value.asText().isBlank();
    }

    private void validateEntityReferences(SemanticResultStore.Section section, JsonNode item) {
        for (String field : ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                requireOwner(SemanticResultStore.Section.ENTITIES, value.asText(), section, field);
            } else if (value.isArray()) {
                value.forEach(reference -> {
                    if (reference.isTextual() && !reference.asText().isBlank()) {
                        requireOwner(
                                SemanticResultStore.Section.ENTITIES,
                                reference.asText(),
                                section,
                                field);
                    } else {
                        throw new SemanticExtractionValidationException(
                                section.wireName + " contains an invalid " + field + " reference");
                    }
                });
            } else if (!value.isMissingNode() && !value.isNull()
                    && !(value.isTextual() && value.asText().isBlank())) {
                throw new SemanticExtractionValidationException(
                        section.wireName + " contains an invalid " + field + " reference");
            }
        }
    }

    private void validateReviewTarget(
            SemanticResultStore.Section source,
            JsonNode item
    ) {
        String targetSection = item.path("targetSection").asText("");
        String targetRef = item.path("targetRef").asText("");
        if (targetSection.isBlank() || targetRef.isBlank()) {
            throw new SemanticExtractionValidationException(
                    "semantic review target section and reference are required");
        }
        SemanticResultStore.Section expected = SemanticResultStore.Section.fromWire(targetSection);
        if (expected != null) {
            requireOwner(expected, targetRef, source, "targetRef");
            return;
        }
        SemanticEvidenceStore.Section evidenceSection = evidenceSection(targetSection);
        if (evidenceSection == null
                || evidenceSection == SemanticEvidenceStore.Section.TABLES
                || !evidenceLookup.containsReference(evidenceSection, targetRef)) {
            throw new SemanticExtractionValidationException(
                    source.wireName + " contains unresolved targetRef reference");
        }
    }

    private SemanticEvidenceStore.Section evidenceSection(String wireName) {
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            if (section.wireName().equals(wireName)) {
                return section;
            }
        }
        return null;
    }

    private void requireOwner(
            SemanticResultStore.Section expected,
            String id,
            SemanticResultStore.Section source,
            String field
    ) {
        if (!sections.get(expected).containsKey(id)) {
            throw new SemanticExtractionValidationException(
                    source.wireName + " contains unresolved " + field + " reference");
        }
    }

}
