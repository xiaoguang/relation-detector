package com.relationdetector.cli;

import java.nio.file.Path;

import com.relationdetector.contracts.Enums.ErrorCode;
import com.relationdetector.contracts.Enums.OutputFormat;

/**
 *
 * CLI entry point. Kept dependency-free for the first implementation drop.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int code = new MainCommand().run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static final class MainCommand {
        int run(String[] args) {
            try {
                if (args.length > 0 && "batch".equals(args[0])) {
                    return new BatchCommand().run(args);
                }
                CliArguments cli = CliArguments.parse(args);
                if (cli.help) {
                    System.out.print(help());
                    return ErrorCode.OK.code();
                }
                if (cli.config == null) {
                    throw new CliFailure(ErrorCode.ARGUMENT_ERROR);
                }
                AdaptorRegistry registry = AdaptorRegistry.load(cli.pluginDir);
                ScanRequest request = new ScanRequest(
                        cli.config,
                        cli.format,
                        cli.output,
                        cli.directOutput,
                        cli.minConfidence,
                        cli.parserMode,
                        cli.grammarProfile,
                        cli.databaseVersion,
                        cli.parallelism);
                SingleScanRunner runner = new SingleScanRunner();
                runner.execute(runner.prepare(request, registry));
                return ErrorCode.OK.code();
            } catch (CliFailure ex) {
                System.err.println(ex.message());
                return ex.code().code();
            } catch (IllegalArgumentException ex) {
                System.err.println("Invalid command or configuration.");
                return ErrorCode.ARGUMENT_ERROR.code();
            } catch (AdaptorRegistry.AdaptorException ex) {
                System.err.println("Requested database adaptor is unavailable.");
                return ErrorCode.ADAPTOR_ERROR.code();
            } catch (Exception ex) {
                System.err.println("Scan execution failed.");
                return ErrorCode.SCAN_RUNTIME_ERROR.code();
            }
        }

        private String help() {
            return """
                    relation-detector scan --config config.yml [--format json|table] [--output result.json] [--plugin-dir plugins]

                    Commands:
                      scan                 Run relationship detection.
                      batch                Run multiple scan configs in one JVM.

                    Options:
                      --config <file>       YAML configuration file. Required.
                      --format <format>     json or table. Overrides output.format.
                      --output <file>       Write output to file. Defaults to stdout.
                      --direct-output <file> Write a direct-only JSON view beside --output.
                      --plugin-dir <dir>    Directory containing external adaptor jars.
                      --min-confidence <n>  Override output.minConfidence.
                      --parser-mode <mode>   auto, full-grammar, or token-event.
                      --grammar-profile <id> Override parser.grammarProfile.
                      --database-version <v> Override parser.databaseVersion.
                      --parallelism <n>       Parse independent file/object/log statements with up to n workers.
                      --help                Show this help.

                    Batch:
                      relation-detector batch --manifest batch.yml [--plugin-dir plugins]
                        [--case-parallelism n] [--max-worker-threads n] [--fail-fast]
                    """;
        }
    }

    static final class CliFailure extends RuntimeException {
        private final ErrorCode code;

        CliFailure(ErrorCode code) {
            this.code = code;
        }

        ErrorCode code() { return code; }

        String message() {
            return switch (code) {
                case ARGUMENT_ERROR -> "Invalid command arguments.";
                case CONFIG_FILE_ERROR -> "Configuration file cannot be read.";
                case CONFIG_FORMAT_ERROR -> "Configuration format is invalid.";
                case INPUT_FILE_ERROR -> "Configured input file cannot be read.";
                case DATABASE_CONNECTION_ERROR -> "Database connection failed.";
                case OUTPUT_WRITE_ERROR -> "Output file cannot be written.";
                default -> "Scan execution failed.";
            };
        }
    }

    static final class CliArguments {
        boolean help;
        Path config;
        OutputFormat format;
        Path output;
        Path directOutput;
        Path pluginDir;
        Double minConfidence;
        String parserMode;
        String grammarProfile;
        String databaseVersion;
        Integer parallelism;

        static CliArguments parse(String[] args) {
            CliArguments parsed = new CliArguments();
            int index = 0;
            if (args.length > 0 && "scan".equals(args[0])) {
                index = 1;
            }
            while (index < args.length) {
                String arg = args[index++];
                switch (arg) {
                    case "--help", "-h" -> parsed.help = true;
                    case "--config" -> parsed.config = Path.of(requireValue(args, index++, arg));
                    case "--format" -> parsed.format = OutputFormat.valueOf(requireValue(args, index++, arg).toUpperCase());
                    case "--output" -> parsed.output = Path.of(requireValue(args, index++, arg));
                    case "--direct-output" -> parsed.directOutput = Path.of(requireValue(args, index++, arg));
                    case "--plugin-dir" -> parsed.pluginDir = Path.of(requireValue(args, index++, arg));
                    case "--min-confidence" -> parsed.minConfidence = Double.parseDouble(requireValue(args, index++, arg));
                    case "--parser-mode" -> parsed.parserMode = normalizeParserMode(requireValue(args, index++, arg));
                    case "--grammar-profile" -> parsed.grammarProfile = requireValue(args, index++, arg);
                    case "--database-version" -> parsed.databaseVersion = requireValue(args, index++, arg);
                    case "--parallelism" -> parsed.parallelism = positiveInt(requireValue(args, index++, arg), arg);
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            return parsed;
        }

        private static String normalizeParserMode(String value) {
            String normalized = value == null || value.isBlank() ? "auto" : value.trim().toLowerCase();
            return switch (normalized) {
                case "auto", "full-grammar", "token-event" -> normalized;
                default -> throw new IllegalArgumentException(
                        "--parser-mode must be one of auto, full-grammar, token-event");
            };
        }

        private static int positiveInt(String value, String option) {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(option + " must be positive");
            }
            return parsed;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
