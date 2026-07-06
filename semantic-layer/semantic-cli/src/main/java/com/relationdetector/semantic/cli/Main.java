package com.relationdetector.semantic.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.semantic.enrich.NoopSemanticEnricher;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.kg.JsonSemanticKgWriter;
import com.relationdetector.semantic.kg.SemanticKgBuilder;
import com.relationdetector.semantic.kg.SemanticKnowledgeGraph;
import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanResultReader;

/** Standalone semantic-layer CLI. It consumes relation-detector JSON only. */
public final class Main {
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
            ScanResultReader reader = new ScanResultReader();
            ScanBundle bundle = arguments.inputs.size() == 1
                    ? reader.read(arguments.inputs.get(0))
                    : reader.readMerged(arguments.inputs);
            EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(bundle);
            evidenceGraph = new NoopSemanticEnricher().enrich(evidenceGraph);
            SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(evidenceGraph);
            new JsonSemanticKgWriter().writeArtifacts(kg, evidenceGraph, arguments.output);
            return 0;
        } catch (IllegalArgumentException ex) {
            System.err.println("Semantic build error: " + ex.getMessage());
            return 2;
        } catch (Exception ex) {
            System.err.println("Semantic build failed: " + ex.getMessage());
            return 1;
        }
    }

    private static String help() {
        return """
                semantic build --input <scan-result.json> [--input <scan-result.json> ...] --output <dir>

                Commands:
                  build                 Build evidence-backed semantic KG JSON from relation-detector JSON.

                Options:
                  --input <file>         relation-detector JSON output. Repeat for same database/schema merge.
                  --output <dir>         Output directory for semantic-kg.json, semantic-build-run.json,
                                         semantic-evidence-graph.json.
                  --help                 Show this help.
                """;
    }

    private record Arguments(List<Path> inputs, Path output, boolean help) {
        static Arguments parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("missing command. Use: semantic build --input <file> --output <dir>");
            }
            int index = 0;
            boolean help = false;
            if ("semantic".equals(args[index])) {
                index++;
            }
            if (index < args.length && "build".equals(args[index])) {
                index++;
            } else if (index < args.length && ("--help".equals(args[index]) || "-h".equals(args[index]))) {
                help = true;
                index++;
            } else {
                throw new IllegalArgumentException("missing build command. Use: semantic build --input <file> --output <dir>");
            }
            List<Path> inputs = new ArrayList<>();
            Path output = null;
            while (index < args.length) {
                String arg = args[index++];
                switch (arg) {
                    case "--help", "-h" -> help = true;
                    case "--input" -> inputs.add(Path.of(requireValue(args, index++, arg)));
                    case "--output" -> output = Path.of(requireValue(args, index++, arg));
                    default -> throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            if (!help && inputs.isEmpty()) {
                throw new IllegalArgumentException("--input is required");
            }
            if (!help && output == null) {
                throw new IllegalArgumentException("--output is required");
            }
            return new Arguments(List.copyOf(inputs), output, help);
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
