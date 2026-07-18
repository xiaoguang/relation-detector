package com.relationdetector.semantic.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.enrich.NoopSemanticEnricher;
import com.relationdetector.semantic.extract.OpenAiResponsesSemanticExtractor;
import com.relationdetector.semantic.extract.SemanticExtractionArtifactWriter;
import com.relationdetector.semantic.extract.SemanticExtractionConfig;
import com.relationdetector.semantic.extract.SemanticExtractionConfigLoader;
import com.relationdetector.semantic.extract.SemanticExtractionDocumentNormalizer;
import com.relationdetector.semantic.extract.SemanticExtractionPrompt;
import com.relationdetector.semantic.extract.SemanticExtractionPromptBuilder;
import com.relationdetector.semantic.extract.SemanticExtractionResult;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.kg.JsonSemanticKgWriter;
import com.relationdetector.semantic.kg.SemanticKgBuilder;
import com.relationdetector.semantic.kg.SemanticKnowledgeGraph;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanResultReader;

/** Standalone semantic-layer CLI. It consumes relation-detector JSON only. */
public final class Main {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args) {
        try {
            Arguments arguments = Arguments.parse(args);
            if (arguments.help) {
                System.out.print(help());
                return 0;
            }
            if (arguments.command == Command.EXTRACT) {
                return runExtract(arguments);
            }
            if (arguments.command == Command.E2E) {
                return runE2e(arguments);
            }
            if (arguments.command == Command.NORMALIZE_EXTRACTION) {
                return runNormalizeExtraction(arguments);
            }
            return runBuild(arguments);
        } catch (IllegalArgumentException ex) {
            System.err.println("Semantic command error: " + ex.getMessage());
            return 2;
        } catch (Exception ex) {
            System.err.println("Semantic command failed: " + ex.getMessage());
            return 1;
        }
    }

    private static int runBuild(Arguments arguments) {
            ScanBundle bundle = readBundle(arguments);
            EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(bundle);
            evidenceGraph = new NoopSemanticEnricher().enrich(evidenceGraph);
            SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(evidenceGraph);
            new JsonSemanticKgWriter().writeArtifacts(kg, evidenceGraph, arguments.output);
            return 0;
    }

    private static int runExtract(Arguments arguments) {
        ScanBundle bundle = readBundle(arguments);
        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(
                bundle,
                arguments.focus,
                arguments.maxRelationships,
                arguments.maxLineage,
                arguments.maxNamingEvidence);
        SemanticExtractionArtifactWriter writer = new SemanticExtractionArtifactWriter();
        if (arguments.provider == ExtractProvider.CODEX_SESSION) {
            writer.writeCodexSessionRequest(arguments.output, prompt);
            return 0;
        }
        String apiKey = arguments.requestOnly ? "" : arguments.apiKey();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                arguments.baseUrl,
                apiKey,
                arguments.model,
                arguments.reasoningEffort,
                arguments.maxOutputTokens);
        if (arguments.requestOnly) {
            writer.writeRequestOnly(arguments.output, prompt, extractor.requestJson(prompt));
            return 0;
        }
        SemanticExtractionResult result = extractor.extract(prompt);
        writer.writeResult(arguments.output, prompt, result);
        return 0;
    }

    private static int runE2e(Arguments arguments) {
        ScanBundle bundle = readBundle(arguments);
        String name = arguments.name().isBlank() ? defaultName(arguments.inputs().get(0)) : arguments.name();
        Path kgOutput = arguments.output.resolve("semantic-kg").resolve(name);
        Path extractionOutput = arguments.output.resolve("semantic-extraction").resolve(name);
        EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(bundle);
        evidenceGraph = new NoopSemanticEnricher().enrich(evidenceGraph);
        SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(evidenceGraph);
        new JsonSemanticKgWriter().writeArtifacts(kg, evidenceGraph, kgOutput);
        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(
                bundle,
                arguments.focus,
                arguments.maxRelationships,
                arguments.maxLineage,
                arguments.maxNamingEvidence);
        new SemanticExtractionArtifactWriter().writeCodexSessionRequest(extractionOutput, prompt);
        return 0;
    }

    private static ScanBundle readBundle(Arguments arguments) {
        ScanResultReader reader = new ScanResultReader();
        return arguments.inputs.size() == 1
                ? reader.read(arguments.inputs.get(0))
                : reader.readMerged(arguments.inputs);
    }

    private static int runNormalizeExtraction(Arguments arguments) {
        if (arguments.inputs.size() != 1) {
            throw new IllegalArgumentException("normalize-extraction requires exactly one --input file");
        }
        try {
            JsonNode raw = JSON.readTree(arguments.inputs.get(0).toFile());
            JsonNode evidenceBundle = JSON.readTree(arguments.evidenceBundle.toFile());
            JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle);
            Path parent = arguments.output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(arguments.output, JSON.writeValueAsString(normalized));
            return 0;
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to normalize semantic extraction result", e);
        }
    }

    private static String help() {
        return """
                semantic build --input <scan-result.json> [--input <scan-result.json> ...] --output <dir>
                semantic extract --config <semantic-extraction.yml>
                semantic extract --input <scan-result.json> --output <dir> [--focus <source>] [--request-only]
                semantic e2e --input <scan-result.json> --output <dir> [--name <case-name>]
                semantic normalize-extraction --input <raw-result.json> --evidence-bundle <bundle.json>
                                              --output <normalized-result.json>

                Commands:
                  build                 Build evidence-backed semantic KG JSON from relation-detector JSON.
                  extract               Build an evidence bundle/prompt. codex-session writes local artifacts;
                                        openai-api calls an OpenAI-compatible Responses API.
                  e2e                   Deterministically write both semantic-kg/<name>/ and
                                        semantic-extraction/<name>/ artifacts without calling a model.
                  normalize-extraction  Convert a JSON semantic extraction result into the formal ref-closed
                                        semantic document shape without calling any model.

                Options:
                  --input <file>         relation-detector JSON output. Repeat for same database/schema merge.
                  --output <dir>         Output directory for semantic-kg.json, semantic-build-run.json,
                                         semantic-evidence-graph.json.
                  --name <case-name>     E2E output case name. Defaults to input file stem.
                  --config <file>        YAML/JSON config for extract. CLI arguments override config values.
                  --focus <source>       Optional routine/query/source focus, for example
                                         ROUTINE:erp_system.sp_rebuild_sales_fact.
                  --provider <name>      codex-session or openai-api. Defaults to codex-session.
                  --model <model>        Model for openai-api. Defaults to OPENAI_MODEL or gpt-5.5.
                  --reasoning-effort <v> Reasoning effort for extract. Defaults to high.
                  --max-output-tokens <n>Maximum model output tokens. Defaults to 12000.
                  --base-url <url>       OpenAI-compatible base URL. Defaults to OPENAI_BASE_URL
                                         or https://api.openai.com/v1.
                  --api-key-env <name>   Environment variable containing API key. Defaults to OPENAI_API_KEY.
                  --max-relationships <n>Evidence relationship cap. Defaults to 0 (unlimited).
                  --max-lineage <n>      Evidence lineage cap. Defaults to 0 (unlimited).
                  --max-naming <n>       Evidence naming cap. Defaults to 0 (unlimited).
                  --request-only         Write prompt/evidence/request artifacts but do not call the model.
                  --evidence-bundle <f>  Required evidence bundle for normalize-extraction.
                  --help                 Show this help.
                """;
    }

    private enum Command {
        BUILD,
        EXTRACT,
        E2E,
        NORMALIZE_EXTRACTION
    }

    private enum ExtractProvider {
        CODEX_SESSION,
        OPENAI_API;

        static ExtractProvider parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase();
            return switch (normalized) {
                case "", "codex", "codex-session" -> CODEX_SESSION;
                case "api", "openai", "openai-api" -> OPENAI_API;
                default -> throw new IllegalArgumentException("unknown extract provider: " + value);
            };
        }
    }

    private record Arguments(
            Command command,
            List<Path> inputs,
            Path output,
            boolean help,
            ExtractProvider provider,
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
            String name,
            Path evidenceBundle
    ) {
        static Arguments parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("missing command. Use: semantic build --input <file> --output <dir>");
            }
            int index = 0;
            boolean help = false;
            if ("semantic".equals(args[index])) {
                index++;
            }
            Command command;
            if (index < args.length && "build".equals(args[index])) {
                command = Command.BUILD;
                index++;
            } else if (index < args.length && "extract".equals(args[index])) {
                command = Command.EXTRACT;
                index++;
            } else if (index < args.length && "e2e".equals(args[index])) {
                command = Command.E2E;
                index++;
            } else if (index < args.length && "normalize-extraction".equals(args[index])) {
                command = Command.NORMALIZE_EXTRACTION;
                index++;
            } else if (index < args.length && ("--help".equals(args[index]) || "-h".equals(args[index]))) {
                command = Command.BUILD;
                help = true;
                index++;
            } else {
                throw new IllegalArgumentException("missing command. Use: semantic build --input <file> --output <dir>");
            }
            List<Path> inputs = new ArrayList<>();
            Path output = null;
            Path config = null;
            ExtractProvider provider = ExtractProvider.CODEX_SESSION;
            String focus = "";
            String model = valueOrDefault(System.getenv("OPENAI_MODEL"), "gpt-5.5");
            String reasoningEffort = "high";
            int maxOutputTokens = 12000;
            String baseUrl = valueOrDefault(System.getenv("OPENAI_BASE_URL"), "https://api.openai.com/v1");
            String apiKeyEnv = "OPENAI_API_KEY";
            int maxRelationships = 0;
            int maxLineage = 0;
            int maxNamingEvidence = 0;
            boolean requestOnly = false;
            String name = "";
            Path evidenceBundle = null;
            boolean providerSet = false;
            boolean focusSet = false;
            boolean modelSet = false;
            boolean reasoningSet = false;
            boolean maxOutputSet = false;
            boolean baseUrlSet = false;
            boolean apiKeyEnvSet = false;
            boolean maxRelationshipsSet = false;
            boolean maxLineageSet = false;
            boolean maxNamingSet = false;
            boolean requestOnlySet = false;
            while (index < args.length) {
                String arg = args[index++];
                switch (arg) {
                    case "--help", "-h" -> help = true;
                    case "--input" -> inputs.add(Path.of(requireValue(args, index++, arg)));
                    case "--output" -> output = Path.of(requireValue(args, index++, arg));
                    case "--config" -> config = Path.of(requireValue(args, index++, arg));
                    case "--provider" -> {
                        provider = ExtractProvider.parse(requireValue(args, index++, arg));
                        providerSet = true;
                    }
                    case "--focus" -> {
                        focus = requireValue(args, index++, arg);
                        focusSet = true;
                    }
                    case "--model" -> {
                        model = requireValue(args, index++, arg);
                        modelSet = true;
                    }
                    case "--reasoning-effort" -> {
                        reasoningEffort = requireValue(args, index++, arg);
                        reasoningSet = true;
                    }
                    case "--max-output-tokens" -> {
                        maxOutputTokens = positiveInt(requireValue(args, index++, arg), arg);
                        maxOutputSet = true;
                    }
                    case "--base-url" -> {
                        baseUrl = requireValue(args, index++, arg);
                        baseUrlSet = true;
                    }
                    case "--api-key-env" -> {
                        apiKeyEnv = requireValue(args, index++, arg);
                        apiKeyEnvSet = true;
                    }
                    case "--max-relationships" -> {
                        maxRelationships = nonNegativeInt(requireValue(args, index++, arg), arg);
                        maxRelationshipsSet = true;
                    }
                    case "--max-lineage" -> {
                        maxLineage = nonNegativeInt(requireValue(args, index++, arg), arg);
                        maxLineageSet = true;
                    }
                    case "--max-naming" -> {
                        maxNamingEvidence = nonNegativeInt(requireValue(args, index++, arg), arg);
                        maxNamingSet = true;
                    }
                    case "--request-only" -> {
                        requestOnly = true;
                        requestOnlySet = true;
                    }
                    case "--name" -> name = requireValue(args, index++, arg);
                    case "--evidence-bundle" -> evidenceBundle = Path.of(requireValue(args, index++, arg));
                    default -> throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            if (command == Command.EXTRACT && config != null) {
                SemanticExtractionConfig loaded = new SemanticExtractionConfigLoader().load(config);
                if (inputs.isEmpty()) {
                    inputs = new ArrayList<>(loaded.inputs());
                }
                if (output == null) {
                    output = loaded.output();
                }
                if (!providerSet) {
                    provider = ExtractProvider.parse(loaded.provider());
                }
                if (!focusSet) {
                    focus = loaded.focus();
                }
                if (!modelSet) {
                    model = loaded.model();
                }
                if (!reasoningSet) {
                    reasoningEffort = loaded.reasoningEffort();
                }
                if (!maxOutputSet) {
                    maxOutputTokens = loaded.maxOutputTokens();
                }
                if (!baseUrlSet) {
                    baseUrl = loaded.baseUrl();
                }
                if (!apiKeyEnvSet) {
                    apiKeyEnv = loaded.apiKeyEnv();
                }
                if (!maxRelationshipsSet) {
                    maxRelationships = loaded.maxRelationships();
                }
                if (!maxLineageSet) {
                    maxLineage = loaded.maxLineage();
                }
                if (!maxNamingSet) {
                    maxNamingEvidence = loaded.maxNamingEvidence();
                }
                if (!requestOnlySet) {
                    requestOnly = loaded.requestOnly();
                }
            }
            if (!help && inputs.isEmpty()) {
                throw new IllegalArgumentException("--input is required");
            }
            if (!help && output == null) {
                throw new IllegalArgumentException("--output is required");
            }
            if (!help && command == Command.NORMALIZE_EXTRACTION && evidenceBundle == null) {
                throw new IllegalArgumentException("--evidence-bundle is required for normalize-extraction");
            }
            return new Arguments(command, List.copyOf(inputs), output, help, provider, focus, model, reasoningEffort,
                    maxOutputTokens, baseUrl, apiKeyEnv, maxRelationships, maxLineage, maxNamingEvidence, requestOnly,
                    name, evidenceBundle);
        }

        String apiKey() {
            String key = System.getenv(apiKeyEnv);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(apiKeyEnv + " is required for semantic extract. Use --request-only "
                        + "to write prompt artifacts without calling the model.");
            }
            return key;
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
    }

    private static String defaultName(Path input) {
        String fileName = input.getFileName() == null ? "scan-result" : input.getFileName().toString();
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }
}
