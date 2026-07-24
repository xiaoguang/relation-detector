package com.relationdetector.semantic.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.semantic.extract.ArtifactRetention;
import com.relationdetector.semantic.extract.SemanticExtractionConfig;
import com.relationdetector.semantic.extract.SemanticExtractionConfigLoader;
import com.relationdetector.semantic.extract.SemanticShardMode;
import com.relationdetector.semantic.extract.SemanticShardingOptions;

/**
 * CN: 解析并合并 semantic CLI 参数与 extract 配置；输入是 argv 和环境默认值，输出不可变命令参数，失败时只抛配置异常，不执行命令或访问 scan 数据。
 * EN: Parses and merges semantic CLI arguments with extract configuration into immutable command input; it performs no command execution or scan-data access.
 */
record SemanticCommandArguments(
        SemanticCommand command,
        List<Path> inputs,
        Path output,
        boolean help,
        SemanticExtractProvider provider,
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
        int maxTransportRetries,
        String name,
        Path evidenceBundle
) {
    static SemanticCommandArguments parse(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("missing semantic command");
        }
        int index = 0;
        boolean help = false;
        if ("semantic".equals(args[index])) {
            index++;
        }
        SemanticCommand command;
        if (index < args.length && "build".equals(args[index])) {
            command = SemanticCommand.BUILD;
            index++;
        } else if (index < args.length && "extract".equals(args[index])) {
            command = SemanticCommand.EXTRACT;
            index++;
        } else if (index < args.length && "e2e".equals(args[index])) {
            command = SemanticCommand.E2E;
            index++;
        } else if (index < args.length && "normalize-extraction".equals(args[index])) {
            command = SemanticCommand.NORMALIZE_EXTRACTION;
            index++;
        } else if (index < args.length && ("--help".equals(args[index]) || "-h".equals(args[index]))) {
            command = SemanticCommand.BUILD;
            help = true;
            index++;
        } else {
            throw new IllegalArgumentException("missing semantic command");
        }

        ParsedValues values = new ParsedValues();
        while (index < args.length) {
            String arg = args[index++];
            switch (arg) {
                case "--help", "-h" -> help = true;
                case "--input" -> values.inputs.add(Path.of(requireValue(args, index++, arg)));
                case "--output" -> values.output = Path.of(requireValue(args, index++, arg));
                case "--config" -> values.config = Path.of(requireValue(args, index++, arg));
                case "--provider" -> {
                    values.provider = SemanticExtractProvider.parse(requireValue(args, index++, arg));
                    values.providerSet = true;
                }
                case "--focus" -> {
                    values.focus = requireValue(args, index++, arg);
                    values.focusSet = true;
                }
                case "--model" -> {
                    values.model = requireValue(args, index++, arg);
                    values.modelSet = true;
                }
                case "--reasoning-effort" -> {
                    values.reasoningEffort = requireValue(args, index++, arg);
                    values.reasoningSet = true;
                }
                case "--max-output-tokens" -> {
                    values.maxOutputTokens = positiveInt(requireValue(args, index++, arg), arg);
                    values.maxOutputSet = true;
                }
                case "--base-url" -> {
                    values.baseUrl = requireValue(args, index++, arg);
                    values.baseUrlSet = true;
                }
                case "--api-key-env" -> {
                    values.apiKeyEnv = requireValue(args, index++, arg);
                    values.apiKeyEnvSet = true;
                }
                case "--max-relationships" -> {
                    values.maxRelationships = nonNegativeInt(requireValue(args, index++, arg), arg);
                    values.maxRelationshipsSet = true;
                }
                case "--max-lineage" -> {
                    values.maxLineage = nonNegativeInt(requireValue(args, index++, arg), arg);
                    values.maxLineageSet = true;
                }
                case "--max-naming" -> {
                    values.maxNamingEvidence = nonNegativeInt(requireValue(args, index++, arg), arg);
                    values.maxNamingSet = true;
                }
                case "--request-only" -> {
                    values.requestOnly = true;
                    values.requestOnlySet = true;
                }
                case "--artifact-retention" -> {
                    values.artifactRetention = ArtifactRetention.parse(requireValue(args, index++, arg));
                    values.artifactRetentionSet = true;
                }
                case "--shard-mode" -> {
                    values.shardMode = SemanticShardMode.parse(requireValue(args, index++, arg));
                    values.shardModeSet = true;
                }
                case "--target-input-tokens" -> {
                    values.targetInputTokens = positiveInt(requireValue(args, index++, arg), arg);
                    values.targetInputTokensSet = true;
                }
                case "--max-input-tokens" -> {
                    values.maxInputTokens = positiveInt(requireValue(args, index++, arg), arg);
                    values.maxInputTokensSet = true;
                }
                case "--max-shards" -> {
                    values.maxShardCount = positiveInt(requireValue(args, index++, arg), arg);
                    values.maxShardCountSet = true;
                }
                case "--shard-max-output-tokens" -> {
                    values.shardMaxOutputTokens = positiveInt(requireValue(args, index++, arg), arg);
                    values.shardMaxOutputSet = true;
                }
                case "--reconciliation-max-output-tokens" -> {
                    values.reconciliationMaxOutputTokens = positiveInt(requireValue(args, index++, arg), arg);
                    values.reconciliationMaxOutputSet = true;
                }
                case "--no-reconcile" -> {
                    values.reconcile = false;
                    values.reconcileSet = true;
                }
                case "--request-timeout-seconds" -> {
                    values.requestTimeoutSeconds = positiveInt(requireValue(args, index++, arg), arg);
                    values.requestTimeoutSet = true;
                }
                case "--max-transport-retries" -> {
                    values.maxTransportRetries = nonNegativeInt(requireValue(args, index++, arg), arg);
                    values.maxTransportRetriesSet = true;
                }
                case "--name" -> values.name = requireValue(args, index++, arg);
                case "--evidence-bundle" -> values.evidenceBundle = Path.of(requireValue(args, index++, arg));
                default -> throw new IllegalArgumentException("unknown semantic argument");
            }
        }
        if (command == SemanticCommand.EXTRACT && values.config != null) {
            values.merge(new SemanticExtractionConfigLoader().load(values.config));
        }
        if (!help && values.inputs.isEmpty()) {
            throw new IllegalArgumentException("semantic input is required");
        }
        if (!help && values.output == null) {
            throw new IllegalArgumentException("semantic output is required");
        }
        if (!help && command == SemanticCommand.NORMALIZE_EXTRACTION && values.evidenceBundle == null) {
            throw new IllegalArgumentException("semantic evidence bundle is required");
        }
        return values.toArguments(command, help);
    }

    String apiKey() {
        String key = System.getenv(apiKeyEnv);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("semantic API key is required");
        }
        return key;
    }

    static String usageText() {
        return """
                semantic build --input <scan-result.json> [--input <scan-result.json> ...] --output <dir>
                semantic extract --config <semantic-extraction.yml>
                semantic extract --input <scan-result.json> --output <dir> [--focus <source>] [--request-only]
                semantic e2e --input <scan-result.json> --output <dir> [--name <case-name>]
                semantic normalize-extraction --input <raw-result.json> --evidence-bundle <bundle.json>
                                              --output <normalized-result.json>

                Commands:
                  build                 Build evidence-backed semantic KG JSON from relation-detector JSON.
                  extract               Build deterministic KG plus evidence-closed model shards. codex-session
                                        writes local artifacts; openai-api calls the Responses API.
                  e2e                   Deterministically write both semantic-kg/<name>/ and
                                        semantic-extraction/<name>/ artifacts without calling a model.
                  normalize-extraction  Convert a JSON semantic extraction result into the formal ref-closed
                                        semantic document shape without calling any model.

                Options:
                  --input <file>         relation-detector JSON output. Repeat for same database/schema merge.
                  --output <dir>         Output directory for semantic artifacts.
                  --name <case-name>     E2E output case name. Defaults to input file stem.
                  --config <file>        YAML/JSON config for extract. CLI arguments override config values.
                  --focus <source>       Optional routine/query/source focus.
                  --provider <name>      codex-session or openai-api. Defaults to codex-session.
                  --model <model>        Approved extraction model; currently fixed to gpt-5.6-sol.
                  --reasoning-effort <v> Approved extraction effort; currently fixed to xhigh.
                  --max-output-tokens <n>Maximum model output tokens. Defaults to 12000.
                  --base-url <url>       OpenAI-compatible base URL.
                  --api-key-env <name>   Environment variable containing API key. Defaults to OPENAI_API_KEY.
                  --max-relationships <n>Evidence relationship cap. Defaults to 0 (unlimited).
                  --max-lineage <n>      Evidence lineage cap. Defaults to 0 (unlimited).
                  --max-naming <n>       Evidence naming cap. Defaults to 0 (unlimited).
                  --request-only         Write request artifacts without calling the model.
                  --artifact-retention <v>
                                         full or final-only. Defaults to full.
                  --shard-mode <mode>    auto, off, or force. Defaults to auto.
                  --target-input-tokens <n>
                                         Preferred conservative estimated input budget. Defaults to 240000.
                  --max-input-tokens <n> Conservative estimated per-shard input limit. Defaults to 800000.
                  --max-shards <n>       Maximum shard count. Defaults to 128.
                  --shard-max-output-tokens <n>
                                         Maximum output tokens for each shard. Defaults to 24000.
                  --reconciliation-max-output-tokens <n>
                                         Maximum output tokens for reconciliation. Defaults to 16000.
                  --no-reconcile         Disable the global reconciliation call for sharded extraction.
                  --request-timeout-seconds <n>
                                         Timeout for one model request. Defaults to 900.
                  --max-transport-retries <n>
                                         Retry count for transport, HTTP 429, and 5xx failures. Defaults to 2.
                  --evidence-bundle <f>  Required evidence bundle for normalize-extraction.
                  --help                 Show this help.
                """;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static int positiveInt(String text, String option) {
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                throw new IllegalArgumentException(option + " must be positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be an integer", e);
        }
    }

    private static int nonNegativeInt(String text, String option) {
        try {
            int value = Integer.parseInt(text);
            if (value < 0) {
                throw new IllegalArgumentException(option + " must be zero or positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be an integer", e);
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class ParsedValues {
        private List<Path> inputs = new ArrayList<>();
        private Path output;
        private Path config;
        private SemanticExtractProvider provider = SemanticExtractProvider.CODEX_SESSION;
        private String focus = "";
        private String model = SemanticExtractionConfig.APPROVED_MODEL;
        private String reasoningEffort = SemanticExtractionConfig.APPROVED_REASONING_EFFORT;
        private int maxOutputTokens = 12000;
        private String baseUrl = valueOrDefault(System.getenv("OPENAI_BASE_URL"), "https://api.openai.com/v1");
        private String apiKeyEnv = "OPENAI_API_KEY";
        private int maxRelationships;
        private int maxLineage;
        private int maxNamingEvidence;
        private boolean requestOnly;
        private ArtifactRetention artifactRetention = ArtifactRetention.FULL;
        private SemanticShardMode shardMode = SemanticShardMode.AUTO;
        private int targetInputTokens = 240000;
        private int maxInputTokens = 800000;
        private int maxShardCount = 128;
        private boolean reconcile = true;
        private int shardMaxOutputTokens = 24000;
        private int reconciliationMaxOutputTokens = 16000;
        private int requestTimeoutSeconds = 900;
        private int maxTransportRetries = 2;
        private String name = "";
        private Path evidenceBundle;
        private boolean providerSet;
        private boolean focusSet;
        private boolean modelSet;
        private boolean reasoningSet;
        private boolean maxOutputSet;
        private boolean baseUrlSet;
        private boolean apiKeyEnvSet;
        private boolean maxRelationshipsSet;
        private boolean maxLineageSet;
        private boolean maxNamingSet;
        private boolean requestOnlySet;
        private boolean artifactRetentionSet;
        private boolean shardModeSet;
        private boolean targetInputTokensSet;
        private boolean maxInputTokensSet;
        private boolean maxShardCountSet;
        private boolean shardMaxOutputSet;
        private boolean reconciliationMaxOutputSet;
        private boolean reconcileSet;
        private boolean requestTimeoutSet;
        private boolean maxTransportRetriesSet;

        private void merge(SemanticExtractionConfig loaded) {
            if (inputs.isEmpty()) inputs = new ArrayList<>(loaded.inputs());
            if (output == null) output = loaded.output();
            if (!providerSet) provider = SemanticExtractProvider.parse(loaded.provider());
            if (!focusSet) focus = loaded.focus();
            if (!modelSet) model = loaded.model();
            if (!reasoningSet) reasoningEffort = loaded.reasoningEffort();
            if (!maxOutputSet) maxOutputTokens = loaded.maxOutputTokens();
            if (!baseUrlSet) baseUrl = loaded.baseUrl();
            if (!apiKeyEnvSet) apiKeyEnv = loaded.apiKeyEnv();
            if (!maxRelationshipsSet) maxRelationships = loaded.maxRelationships();
            if (!maxLineageSet) maxLineage = loaded.maxLineage();
            if (!maxNamingSet) maxNamingEvidence = loaded.maxNamingEvidence();
            if (!requestOnlySet) requestOnly = loaded.requestOnly();
            if (!artifactRetentionSet) artifactRetention = loaded.artifactRetention();
            if (!shardModeSet) shardMode = loaded.sharding().mode();
            if (!targetInputTokensSet) targetInputTokens = loaded.sharding().targetInputTokens();
            if (!maxInputTokensSet) maxInputTokens = loaded.sharding().maxInputTokens();
            if (!maxShardCountSet) maxShardCount = loaded.sharding().maxShardCount();
            if (!reconcileSet) reconcile = loaded.sharding().reconcile();
            if (!shardMaxOutputSet) shardMaxOutputTokens = loaded.shardMaxOutputTokens();
            if (!reconciliationMaxOutputSet) {
                reconciliationMaxOutputTokens = loaded.reconciliationMaxOutputTokens();
            }
            if (!requestTimeoutSet) requestTimeoutSeconds = loaded.requestTimeoutSeconds();
            if (!maxTransportRetriesSet) maxTransportRetries = loaded.maxTransportRetries();
        }

        private SemanticCommandArguments toArguments(SemanticCommand command, boolean help) {
            SemanticShardingOptions sharding = new SemanticShardingOptions(
                    shardMode, targetInputTokens, maxInputTokens, maxShardCount, reconcile);
            SemanticExtractionConfig validated = new SemanticExtractionConfig(
                    provider == SemanticExtractProvider.OPENAI_API ? "openai-api" : "codex-session",
                    inputs, output, focus, model, reasoningEffort, maxOutputTokens, baseUrl, apiKeyEnv,
                    maxRelationships, maxLineage, maxNamingEvidence, requestOnly, artifactRetention, sharding,
                    shardMaxOutputTokens, reconciliationMaxOutputTokens, requestTimeoutSeconds,
                    maxTransportRetries);
            return new SemanticCommandArguments(command, validated.inputs(), validated.output(), help, provider,
                    validated.focus(), validated.model(), validated.reasoningEffort(), validated.maxOutputTokens(),
                    validated.baseUrl(), validated.apiKeyEnv(), validated.maxRelationships(), validated.maxLineage(),
                    validated.maxNamingEvidence(), validated.requestOnly(), validated.artifactRetention(),
                    validated.sharding(),
                    validated.shardMaxOutputTokens(), validated.reconciliationMaxOutputTokens(),
                    validated.requestTimeoutSeconds(), validated.maxTransportRetries(), name, evidenceBundle);
        }
    }
}

enum SemanticCommand {
    BUILD,
    EXTRACT,
    E2E,
    NORMALIZE_EXTRACTION
}

enum SemanticExtractProvider {
    CODEX_SESSION,
    OPENAI_API;

    static SemanticExtractProvider parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "", "codex", "codex-session" -> CODEX_SESSION;
            case "api", "openai", "openai-api" -> OPENAI_API;
            default -> throw new IllegalArgumentException("unknown semantic extract provider");
        };
    }
}
