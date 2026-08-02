package com.relationdetector.semantic.extraction.prompt;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 为 Codex-session 请求生成与 typed extraction DTO 和受限 reconciliation patch 完全一致的 JSON Schema；
 * 输入是固定内部 wire contract，输出交给 request artifact writer，禁止放宽 unknown-field 或业务 evidence 校验。
 * EN: Generates JSON Schemas that exactly match the typed extraction DTO and constrained reconciliation patch.
 * Request artifact writers consume these fixed wire contracts; this helper must not relax unknown-field or evidence
 * validation.
 */
public final class SemanticModelOutputSchema {
    public static final String EXTRACTION_FILE = "semantic-extraction-output-schema.json";
    public static final String RECONCILIATION_FILE = "semantic-reconciliation-output-schema.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> BASE_FIELDS = List.of(
            "id", "name", "type", "machineType", "description", "reviewStatus", "severity",
            "ownedGroundingRefs", "evidenceRefs");

    public ObjectNode extraction() {
        ObjectNode root = object();
        ObjectNode properties = root.putObject("properties");
        properties.set("entities", arrayOf(item(List.of("physicalName"), List.of())));
        ObjectNode event = item(
                List.of("physicalName", "eventCandidateRef"),
                List.of("inputs", "outputs", "inputEntityRefs", "outputEntityRefs"));
        ObjectNode eventProperties = (ObjectNode) event.path("properties");
        eventProperties.set("inputs", emptyEventEndpointArray());
        eventProperties.set("outputs", emptyEventEndpointArray());
        eventProperties.set("inputEntityRefs", emptyEventEndpointArray());
        eventProperties.set("outputEntityRefs", emptyEventEndpointArray());
        properties.set("events", arrayOf(event));
        properties.set("relations", arrayOf(item(
                List.of("from", "to", "fromEntityRef", "toEntityRef"), List.of())));
        properties.set("lineage", arrayOf(item(
                List.of("to", "toPhysical", "transform", "targetEntityRef"),
                List.of("from", "fromPhysical", "sourceEntityRefs"))));
        properties.set("metrics", arrayOf(item(
                List.of("physicalField", "ownerEntityRef"),
                List.of("sourceFields", "sourceEntityRefs"))));
        properties.set("dimensions", arrayOf(item(
                List.of("physicalField", "dimensionTable", "ownerEntityRef", "dimensionEntityRef"), List.of())));
        properties.set("triplets", arrayOf(item(
                List.of("candidateRef", "subject", "predicate", "object", "readable", "subjectRef", "objectRef"),
                List.of())));
        properties.set("reviewItems", arrayOf(item(
                List.of("targetRef", "targetSection", "target", "section", "reason"), List.of())));
        properties.set("semanticGraph", nullOnly());
        properties.set("validation", nullOnly());
        requireAll(root, properties);
        return root;
    }

    public ObjectNode reconciliation() {
        ObjectNode root = object();
        ObjectNode properties = root.putObject("properties");
        properties.set("resolutions", arrayOf(objectWithNullableStrings(
                "section", "id", "selectedVariantHash")));
        properties.set("renames", arrayOf(objectWithNullableStrings(
                "section", "id", "name", "description")));
        requireAll(root, properties);
        return root;
    }

    private ObjectNode item(List<String> stringFields, List<String> listFields) {
        ObjectNode item = object();
        ObjectNode properties = item.putObject("properties");
        for (String field : BASE_FIELDS) {
            properties.set(field, switch (field) {
                case "ownedGroundingRefs" -> stringArray(1);
                case "evidenceRefs" -> stringArray(0);
                default -> nullableString();
            });
        }
        stringFields.forEach(field -> properties.set(field, nullableString()));
        listFields.forEach(field -> properties.set(field, nullableStringArray()));
        requireAll(item, properties);
        return item;
    }

    private ObjectNode objectWithNullableStrings(String... fields) {
        ObjectNode value = object();
        ObjectNode properties = value.putObject("properties");
        for (String field : fields) properties.set(field, nullableString());
        requireAll(value, properties);
        return value;
    }

    private ObjectNode object() {
        ObjectNode value = JSON.createObjectNode();
        value.put("type", "object");
        value.put("additionalProperties", false);
        return value;
    }

    private ObjectNode arrayOf(ObjectNode item) {
        ObjectNode value = JSON.createObjectNode();
        value.put("type", "array");
        value.set("items", item);
        return value;
    }

    private ObjectNode stringArray(int minimumItems) {
        ObjectNode value = JSON.createObjectNode();
        value.put("type", "array");
        value.set("items", JSON.createObjectNode().put("type", "string"));
        value.put("minItems", minimumItems);
        return value;
    }

    private ObjectNode nullableStringArray() {
        ObjectNode value = JSON.createObjectNode();
        value.set("type", JSON.createArrayNode().add("array").add("null"));
        value.set("items", JSON.createObjectNode().put("type", "string"));
        return value;
    }

    private ObjectNode emptyEventEndpointArray() {
        ObjectNode value = stringArray(0);
        value.put("maxItems", 0);
        value.put("description", "Leave empty; the core rebuilds deterministic event endpoints from eventCandidateRef.");
        return value;
    }

    private ObjectNode nullableString() {
        ObjectNode value = JSON.createObjectNode();
        value.set("type", JSON.createArrayNode().add("string").add("null"));
        return value;
    }

    private ObjectNode nullOnly() {
        return JSON.createObjectNode().put("type", "null");
    }

    private void requireAll(ObjectNode owner, ObjectNode properties) {
        ArrayNode required = owner.putArray("required");
        properties.fieldNames().forEachRemaining(required::add);
    }
}
