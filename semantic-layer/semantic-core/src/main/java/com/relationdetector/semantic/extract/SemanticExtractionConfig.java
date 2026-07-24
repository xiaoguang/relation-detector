package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.util.List;

/**
 * CN: semantic extraction command 的不可变 runtime config，规范 provider、inputs、model、limits 和 output defaults；不读取环境变量或执行请求。
 * EN: Immutable runtime configuration for semantic extraction commands, normalizing provider, inputs, model, limits, and output defaults. It neither reads environment variables nor executes requests.
 */
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
        boolean requestOnly,
        ArtifactRetention artifactRetention,
        SemanticShardingOptions sharding,
        int shardMaxOutputTokens,
        int reconciliationMaxOutputTokens,
        int requestTimeoutSeconds,
        int maxTransportRetries
) {
    public static final String APPROVED_MODEL = "gpt-5.6-sol";
    public static final String APPROVED_REASONING_EFFORT = "xhigh";

    public SemanticExtractionConfig {
        provider = blankDefault(provider, "codex-session");
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        focus = blankDefault(focus, "");
        model = blankDefault(model, APPROVED_MODEL);
        reasoningEffort = blankDefault(reasoningEffort, APPROVED_REASONING_EFFORT);
        if (!APPROVED_MODEL.equals(model)) {
            throw new IllegalArgumentException("semantic extraction model must be " + APPROVED_MODEL);
        }
        if (!APPROVED_REASONING_EFFORT.equals(reasoningEffort)) {
            throw new IllegalArgumentException(
                    "semantic extraction reasoningEffort must be " + APPROVED_REASONING_EFFORT);
        }
        requirePositive(maxOutputTokens, "maxOutputTokens");
        baseUrl = blankDefault(baseUrl, "https://api.openai.com/v1");
        apiKeyEnv = blankDefault(apiKeyEnv, "OPENAI_API_KEY");
        requireNonNegative(maxRelationships, "maxRelationships");
        requireNonNegative(maxLineage, "maxLineage");
        requireNonNegative(maxNamingEvidence, "maxNamingEvidence");
        artifactRetention = artifactRetention == null ? ArtifactRetention.FULL : artifactRetention;
        sharding = sharding == null ? SemanticShardingOptions.defaults() : sharding;
        requirePositive(shardMaxOutputTokens, "shardMaxOutputTokens");
        requirePositive(reconciliationMaxOutputTokens, "reconciliationMaxOutputTokens");
        requirePositive(requestTimeoutSeconds, "requestTimeoutSeconds");
        requireNonNegative(maxTransportRetries, "maxTransportRetries");
    }

    public static SemanticExtractionConfig defaults() {
        return new SemanticExtractionConfig("codex-session", List.of(), null, "",
                APPROVED_MODEL, APPROVED_REASONING_EFFORT, 12000,
                "https://api.openai.com/v1", "OPENAI_API_KEY", 0, 0, 0, false, ArtifactRetention.FULL,
                SemanticShardingOptions.defaults(), 24000, 16000, 900, 2);
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be zero or positive");
        }
    }
}
