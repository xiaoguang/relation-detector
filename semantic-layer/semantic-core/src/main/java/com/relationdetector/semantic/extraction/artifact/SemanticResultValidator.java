package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.artifact.SemanticResultSelection;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

/**
 * CN: 对完成选择后的 path-backed semantic sections执行证据引用和typed section引用闭包校验。
 * 输入是外排section stores与完整evidence store，成功无输出，失败原子拒绝最终发布；本类不合并variant、
 * 不应用reconciliation patch，也不写最终文档。
 * EN: Validates evidence and typed section reference closure over selected path-backed semantic
 * sections. It rejects publication on failure but neither merges variants nor applies patches or renders artifacts.
 */
public final class SemanticResultValidator {
    static final List<String> ENTITY_REF_FIELDS = List.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef");
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
                    validateReferences(section, selection.selectedDocument(section, record.value())));
        }
    }

    private void validateReferences(SemanticResultStore.Section section, JsonNode item) {
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
                    }
                });
            }
        }
        if (section == SemanticResultStore.Section.REVIEW_ITEMS) {
            String targetSection = item.path("targetSection").asText("");
            String targetRef = item.path("targetRef").asText("");
            SemanticResultStore.Section expected =
                    SemanticResultStore.Section.fromWire(targetSection);
            if (expected == null || targetRef.isBlank()) {
                throw new SemanticExtractionValidationException(
                        "semantic review target section and reference are required");
            }
            requireOwner(expected, targetRef, section, "targetRef");
        }
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
