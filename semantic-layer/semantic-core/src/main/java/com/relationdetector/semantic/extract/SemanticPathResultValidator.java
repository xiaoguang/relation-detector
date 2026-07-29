package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 对完成选择后的 path-backed semantic sections执行证据引用、typed section引用和孤立实体闭包校验。
 * 输入是外排section stores与完整evidence store，成功无输出，失败原子拒绝最终发布；本类不合并variant、
 * 不应用reconciliation patch，也不写最终文档。
 * EN: Validates evidence, typed section references, and isolated-entity closure over selected path-backed semantic
 * sections. It rejects publication on failure but neither merges variants nor applies patches or renders artifacts.
 */
final class SemanticPathResultValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ENTITY_REF_FIELDS = List.of(
            "inputEntityRefs", "outputEntityRefs", "fromEntityRef", "toEntityRef",
            "sourceEntityRefs", "targetEntityRef", "ownerEntityRef", "dimensionEntityRef",
            "subjectRef", "objectRef");
    private final Path workspace;
    private final SemanticEvidenceStore evidenceStore;
    private final Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections;
    private final SemanticPathResultSelection selection;

    SemanticPathResultValidator(
            Path workspace,
            SemanticEvidenceStore evidenceStore,
            Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections,
            SemanticPathResultSelection selection
    ) {
        this.workspace = workspace;
        this.evidenceStore = evidenceStore;
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
            if (reference.isBlank() || !evidenceStore.containsReference(reference)) {
                throw new SemanticExtractionValidationException(
                        "semantic item contains unresolved evidence reference");
            }
        }
    }

    void validate() {
        validateSemanticReferences();
        validateNoIsolatedEntities();
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

    private void validateNoIsolatedEntities() {
        try (ExternalJsonRecordStore linked = new ExternalJsonRecordStore(
                workspace.resolve("linked-entities"))) {
            for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
                if (section == SemanticPathResultStore.Section.ENTITIES
                        || section == SemanticPathResultStore.Section.REVIEW_ITEMS) {
                    continue;
                }
                sections.get(section).forEach(record ->
                        appendEntityRefs(selection.selectedDocument(section, record.value()), linked));
            }
            linked.finish();
            sections.get(SemanticPathResultStore.Section.ENTITIES).forEach(record -> {
                if (!linked.containsKey(record.key())) {
                    throw new SemanticExtractionValidationException(
                            "semantic extraction contains an isolated entity");
                }
            });
        }
    }

    private void appendEntityRefs(JsonNode item, ExternalJsonRecordStore linked) {
        for (String field : ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                appendEntityRef(linked, value);
            } else if (value.isArray()) {
                value.forEach(reference -> appendEntityRef(linked, reference));
            }
        }
    }

    private void appendEntityRef(ExternalJsonRecordStore linked, JsonNode reference) {
        if (reference.isTextual() && !reference.asText().isBlank()) {
            linked.append(
                    reference.asText(),
                    JSON.getNodeFactory().textNode(reference.asText()));
        }
    }
}
