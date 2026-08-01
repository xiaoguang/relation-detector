package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SemanticExtractionPromptBuilderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rendersEvidenceBundleAsCompactJsonWithoutChangingItsContent() {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.putObject("database").put("type", "MYSQL");
        bundle.putArray("items").addObject().put("id", "fact-1");

        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(bundle);

        assertTrue(prompt.userPrompt().contains(
                "{\"database\":{\"type\":\"MYSQL\"},\"items\":[{\"id\":\"fact-1\"}]}"));
    }
}
