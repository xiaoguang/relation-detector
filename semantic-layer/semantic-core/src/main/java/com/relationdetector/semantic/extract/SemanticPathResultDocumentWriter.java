package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.ScanResultContractException;

/**
 * CN: 从已完成且闭合的外排 semantic sections流式写 merged/final 文档，并为final输出重建节点和typed edge。
 * 输入一次仅保留单条记录，输出是有序JSON文件；上游完成选择与闭包校验，下游读取正式artifact。
 * 本类不选择冲突、不修改语义对象，也不判断evidence有效性。
 * EN: Streams merged and final documents from closed external semantic sections and rebuilds typed graph records for
 * final output one item at a time. Selection and closure are upstream responsibilities; this writer never changes
 * semantic objects or validates evidence.
 */
final class SemanticPathResultDocumentWriter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections;
    private final SemanticPathResultSelection selection;

    SemanticPathResultDocumentWriter(
            Path workspace,
            Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> sections,
            SemanticPathResultSelection selection
    ) {
        this.workspace = workspace;
        this.sections = sections;
        this.selection = selection;
    }

    void write(Path target, boolean includeGraph) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
                generator.useDefaultPrettyPrinter();
                generator.writeStartObject();
                for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
                    writeSection(generator, section);
                }
                if (includeGraph) {
                    writeGraph(generator);
                    writeValidation(generator);
                }
                generator.writeEndObject();
                generator.writeRaw('\n');
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to stream semantic extraction result", failure);
        }
    }

    private void writeGraph(JsonGenerator generator) throws IOException {
        try (ExternalJsonRecordStore nodes = new ExternalJsonRecordStore(workspace.resolve("graph-nodes"));
             ExternalJsonRecordStore edges = new ExternalJsonRecordStore(workspace.resolve("graph-edges"))) {
            for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
                sections.get(section).forEach(record -> addGraphRecords(
                        section,
                        selection.renamed(
                                section,
                                record.key(),
                                selection.selectedDocument(section, record.value())),
                        nodes,
                        edges));
            }
            nodes.finish();
            edges.finish();
            generator.writeObjectFieldStart("semanticGraph");
            nodes.writeArray(generator, "nodes");
            edges.writeArray(generator, "edges");
            generator.writeObjectFieldStart("summary");
            generator.writeNumberField("nodeCount", nodes.count());
            generator.writeNumberField("edgeCount", edges.count());
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private void writeSection(
            JsonGenerator generator,
            SemanticPathResultStore.Section section
    ) throws IOException {
        generator.writeArrayFieldStart(section.wireName);
        sections.get(section).forEach(record -> {
            try {
                generator.writeTree(selection.renamed(
                        section,
                        record.key(),
                        selection.selectedDocument(section, record.value())));
            } catch (IOException failure) {
                throw new ScanResultContractException(
                        "failed to stream normalized semantic section",
                        failure);
            }
        });
        generator.writeEndArray();
    }

    private void writeValidation(JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("validation");
        writeIsolatedEntities(generator);
        generator.writeArrayFieldStart("unresolvedReferences");
        generator.writeEndArray();
        generator.writeArrayFieldStart("missingEvidenceRefs");
        generator.writeEndArray();
        generator.writeNumberField("generatedReviewItemCount", 0);
        generator.writeBooleanField("isRefClosed", true);
        generator.writeEndObject();
    }

    private void writeIsolatedEntities(JsonGenerator generator) throws IOException {
        try (ExternalJsonRecordStore linked = new ExternalJsonRecordStore(
                workspace.resolve("validation-linked-entities"))) {
            for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
                if (section == SemanticPathResultStore.Section.ENTITIES
                        || section == SemanticPathResultStore.Section.REVIEW_ITEMS) {
                    continue;
                }
                sections.get(section).forEach(record ->
                        appendEntityReferences(selection.selectedDocument(section, record.value()), linked));
            }
            linked.finish();
            generator.writeArrayFieldStart("isolatedEntities");
            sections.get(SemanticPathResultStore.Section.ENTITIES).forEach(record -> {
                if (linked.containsKey(record.key())) return;
                JsonNode entity = selection.renamed(
                        SemanticPathResultStore.Section.ENTITIES,
                        record.key(),
                        selection.selectedDocument(SemanticPathResultStore.Section.ENTITIES, record.value()));
                try {
                    generator.writeStartObject();
                    generator.writeStringField("id", record.key());
                    generator.writeStringField("name", entity.path("name").asText(""));
                    generator.writeStringField("physicalName", entity.path("physicalName").asText(""));
                    generator.writeStringField(
                            "reason",
                            "Entity has evidence but is not referenced by another semantic fact.");
                    generator.writeEndObject();
                } catch (IOException failure) {
                    throw new ScanResultContractException(
                            "failed to stream isolated semantic entity",
                            failure);
                }
            });
            generator.writeEndArray();
        }
    }

    private void appendEntityReferences(JsonNode item, ExternalJsonRecordStore linked) {
        for (String field : SemanticPathResultValidator.ENTITY_REF_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                appendEntityReference(linked, value.asText());
            } else if (value.isArray()) {
                value.forEach(reference -> {
                    if (reference.isTextual() && !reference.asText().isBlank()) {
                        appendEntityReference(linked, reference.asText());
                    }
                });
            }
        }
    }

    private void appendEntityReference(ExternalJsonRecordStore linked, String reference) {
        linked.append(reference, JSON.getNodeFactory().textNode(reference));
    }

    private void addGraphRecords(
            SemanticPathResultStore.Section section,
            JsonNode item,
            ExternalJsonRecordStore nodes,
            ExternalJsonRecordStore edges
    ) {
        String id = item.path("id").asText("");
        ObjectNode node = JSON.createObjectNode();
        node.put("id", id);
        node.put("kind", section.graphKind);
        node.put("label", graphLabel(section, item));
        node.put("type", graphType(section, item));
        node.set("evidenceRefs", item.path("evidenceRefs").deepCopy());
        nodes.append(id, node);
        addGraphEdges(section, item, edges, id);
    }

    private String graphLabel(SemanticPathResultStore.Section section, JsonNode item) {
        return switch (section) {
            case ENTITIES, EVENTS, METRICS, DIMENSIONS -> item.path("name").asText("");
            case RELATIONS -> item.path("type").asText("");
            case LINEAGE -> item.path("to").asText("");
            case TRIPLETS -> item.path("readable").asText("");
            case REVIEW_ITEMS -> item.path("targetRef").asText("");
        };
    }

    private String graphType(SemanticPathResultStore.Section section, JsonNode item) {
        return switch (section) {
            case ENTITIES, EVENTS, RELATIONS, METRICS, DIMENSIONS -> item.path("type").asText("");
            case LINEAGE -> item.path("transform").asText("");
            case TRIPLETS -> item.path("predicate").asText("");
            case REVIEW_ITEMS -> "REVIEW_NEEDED";
        };
    }

    /**
     * CN: 按已规范化section把一个semantic对象展开为确定类型的KG边，并将边写入外排record store；
     * entity没有出边，其余section只读取各自typed reference字段。缺失可选引用不会写边，重复或冲突
     * identity由store统一拒绝；本方法不通过名称或展示文本补推引用。
     *
     * EN: Expands one normalized semantic object into typed KG edges for its section and appends them to the external
     * record store. Entities emit no outgoing edge, while every other section reads only its typed reference fields.
     * Missing optional references emit nothing; the store rejects duplicate or conflicting identities, and this
     * method never infers references from names or display text.
     */
    private void addGraphEdges(
            SemanticPathResultStore.Section section,
            JsonNode item,
            ExternalJsonRecordStore edges,
            String id
    ) {
        switch (section) {
            case EVENTS -> {
                addEdges(edges, "event-input", id, item.path("inputEntityRefs"), "EVENT_INPUT", item);
                addEdges(edges, "event-output", id, item.path("outputEntityRefs"), "EVENT_OUTPUT", item);
            }
            case RELATIONS -> {
                addEdge(edges, "relation-from", id, item.path("fromEntityRef").asText(""),
                        "RELATION_FROM", item);
                addEdge(edges, "relation-to", id, item.path("toEntityRef").asText(""),
                        "RELATION_TO", item);
                addOwnedEdge(edges, "relation", id, item.path("fromEntityRef").asText(""),
                        item.path("toEntityRef").asText(""),
                        SemanticNormalizationSupport.nonBlank(
                                item.path("type").asText(""),
                                "RELATES_TO"),
                        item);
            }
            case LINEAGE -> {
                addEdges(edges, "lineage-source", id, item.path("sourceEntityRefs"), "LINEAGE_SOURCE", item);
                addEdge(edges, "lineage-target", id, item.path("targetEntityRef").asText(""),
                        "LINEAGE_TARGET", item);
            }
            case METRICS -> addEdge(edges, "metric-owner", id, item.path("ownerEntityRef").asText(""),
                    "METRIC_OWNER", item);
            case DIMENSIONS -> {
                addEdge(edges, "dimension-owner", id, item.path("ownerEntityRef").asText(""),
                        "DIMENSION_OWNER", item);
                addEdge(edges, "dimension-target", id, item.path("dimensionEntityRef").asText(""),
                        "DIMENSION_TARGET", item);
            }
            case TRIPLETS -> {
                addEdge(edges, "triplet-subject", id, item.path("subjectRef").asText(""),
                        "TRIPLET_SUBJECT", item);
                addEdge(edges, "triplet-object", id, item.path("objectRef").asText(""),
                        "TRIPLET_OBJECT", item);
            }
            case REVIEW_ITEMS -> addEdge(edges, "review-target", id, item.path("targetRef").asText(""),
                    "REVIEW_TARGET", item);
            case ENTITIES -> {
                // Entity nodes own no outgoing typed graph edge.
            }
        }
    }

    private void addEdges(
            ExternalJsonRecordStore edges,
            String prefix,
            String source,
            JsonNode targets,
            String type,
            JsonNode owner
    ) {
        if (!targets.isArray()) {
            return;
        }
        targets.forEach(target -> {
            if (target.isTextual()) {
                addEdge(edges, prefix, source, target.asText(), type, owner);
            }
        });
    }

    private void addEdge(
            ExternalJsonRecordStore edges,
            String prefix,
            String source,
            String target,
            String type,
            JsonNode owner
    ) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return;
        }
        String id = StableSemanticId.of("semantic-edge", prefix, source, target, type);
        ObjectNode edge = JSON.createObjectNode();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.set("evidenceRefs", owner.path("evidenceRefs").deepCopy());
        edges.append(id, edge);
    }

    private void addOwnedEdge(
            ExternalJsonRecordStore edges,
            String prefix,
            String owner,
            String source,
            String target,
            String type,
            JsonNode item
    ) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return;
        }
        String id = SemanticCanonicalIdentity.ownedEdge(prefix, owner, source, target, type);
        ObjectNode edge = JSON.createObjectNode();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.set("evidenceRefs", item.path("evidenceRefs").deepCopy());
        edges.append(id, edge);
    }
}
