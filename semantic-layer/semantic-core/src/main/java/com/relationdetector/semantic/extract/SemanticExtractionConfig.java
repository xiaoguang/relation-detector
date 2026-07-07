package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.util.List;

/** Runtime configuration for semantic extraction. */
public record SemanticExtractionConfig(
        String provider,
        List<Path> inputs,
        Path output,
        String focus,
        String model,
        String reasoningEffort,
        int maxOutputTokens,
        String baseUrl,
        String apiKeyEnv,
        int maxRelationships,
        int maxLineage,
        int maxNamingEvidence,
        boolean requestOnly
) {
    public SemanticExtractionConfig {
        provider = blankDefault(provider, "codex-session");
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        focus = blankDefault(focus, "");
        model = blankDefault(model, "gpt-5.5");
        reasoningEffort = blankDefault(reasoningEffort, "high");
        maxOutputTokens = positiveDefault(maxOutputTokens, 12000);
        baseUrl = blankDefault(baseUrl, "https://api.openai.com/v1");
        apiKeyEnv = blankDefault(apiKeyEnv, "OPENAI_API_KEY");
        maxRelationships = positiveDefault(maxRelationships, 80);
        maxLineage = positiveDefault(maxLineage, 80);
        maxNamingEvidence = positiveDefault(maxNamingEvidence, 80);
    }

    public static SemanticExtractionConfig defaults() {
        return new SemanticExtractionConfig("codex-session", List.of(), null, "", "gpt-5.5", "high", 12000,
                "https://api.openai.com/v1", "OPENAI_API_KEY", 80, 80, 80, false);
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int positiveDefault(int value, int defaultValue) {
        return value <= 0 ? defaultValue : value;
    }
}
