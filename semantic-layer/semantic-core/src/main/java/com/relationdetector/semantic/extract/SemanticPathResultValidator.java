package com.relationdetector.semantic.extract;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;

/**
 * CN: 对完成选择后的 path-backed semantic sections执行证据引用和typed section引用闭包校验。
 * 输入是外排section stores与完整evidence store，成功无输出，失败原子拒绝最终发布；本类不合并variant、
 * 不应用reconciliation patch，也不写最终文档。
 * EN: Validates evidence and typed section reference closure over selected path-backed semantic
 * sections. It rejects publication on failure but neither merges variants nor applies patches or renders artifacts.
 */
final class SemanticPathResultValidator {
    static final List<String> ENTITY_REF_FIELDS = List.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef");
    private final SemanticEvidenceLookup evidenceLookup;
    private final Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections;
    private final SemanticPathResultSelection selection;

    SemanticPathResultValidator(
            SemanticEvidenceLookup evidenceLookup,
            Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections,
            SemanticPathResultSelection selection
    ) {
        this.evidenceLookup = evidenceLookup;
        this.sections = sections;
        this.selection = selection;
    }

    void requireEvidence(
            SemanticPathResultStore.Section section,
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

    void validate() {
        validateSemanticReferences();
    }

    private void validateSemanticReferences() {
        for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
            sections.get(section).forEach(record ->
                    validateReferences(section, selection.selectedDocument(section, record.value())));
        }
    }

    private void validateReferences(SemanticPathResultStore.Section section, JsonNode item) {
        for (String field : ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                requireOwner(SemanticPathResultStore.Section.ENTITIES, value.asText(), section, field);
            } else if (value.isArray()) {
                value.forEach(reference -> {
                    if (reference.isTextual() && !reference.asText().isBlank()) {
                        requireOwner(
                                SemanticPathResultStore.Section.ENTITIES,
                                reference.asText(),
                                section,
                                field);
                    }
                });
            }
        }
        if (section == SemanticPathResultStore.Section.REVIEW_ITEMS) {
            String targetSection = item.path("targetSection").asText("");
            String targetRef = item.path("targetRef").asText("");
            SemanticPathResultStore.Section expected =
                    SemanticPathResultStore.Section.fromWire(targetSection);
            if (expected == null || targetRef.isBlank()) {
                throw new SemanticExtractionValidationException(
                        "semantic review target section and reference are required");
            }
            requireOwner(expected, targetRef, section, "targetRef");
        }
    }

    private void requireOwner(
            SemanticPathResultStore.Section expected,
            String id,
            SemanticPathResultStore.Section source,
            String field
    ) {
        if (!sections.get(expected).containsKey(id)) {
            throw new SemanticExtractionValidationException(
                    source.wireName + " contains unresolved " + field + " reference");
        }
    }

}
