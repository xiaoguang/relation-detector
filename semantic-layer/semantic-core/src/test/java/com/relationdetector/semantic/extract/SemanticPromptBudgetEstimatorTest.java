package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticPromptBudgetEstimatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticPromptBudgetEstimator estimator = new SemanticPromptBudgetEstimator();

    @Test
    void estimatesAsciiAndCjkConservativelyWithStableSafetyMargin() {
        int ascii = estimator.estimate("a".repeat(400));
        int cjk = estimator.estimate("订单".repeat(200));

        assertEquals(ascii, estimator.estimate("a".repeat(400)));
        assertTrue(ascii >= 115);
        assertTrue(cjk > ascii);
    }

    @Test
    void estimatesTheFinalRenderedDeveloperAndUserPromptTogether() {
        SemanticExtractionPrompt prompt = new SemanticExtractionPrompt(
                "developer-contract-" + "d".repeat(400),
                "user-evidence-" + "u".repeat(800),
                JSON.createObjectNode());

        assertEquals(
                estimator.estimate(prompt.developerPrompt() + "\n" + prompt.userPrompt()),
                estimator.estimate(prompt));
        assertTrue(estimator.estimate(prompt) > estimator.estimate(prompt.userPrompt()));
    }
}
