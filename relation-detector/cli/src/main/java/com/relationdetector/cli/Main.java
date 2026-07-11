package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.Enums.ErrorCode;
import com.relationdetector.contracts.Enums.OutputFormat;
import com.relationdetector.core.output.JsonResultWriter;
import com.relationdetector.core.scan.ScanEngine;
import com.relationdetector.core.output.TableResultWriter;

/** CLI entry point. Kept dependency-free for the first implementation drop. */
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
            CliArguments cli = CliArguments.parse(args);
            if (cli.help) {
                System.out.print(help());
                return ErrorCode.OK.code();
            }
            if (cli.config == null) {
                System.err.println("Missing required --config. Use --help for usage.");
                return ErrorCode.ARGUMENT_ERROR.code();
            }

            try {
                var config = new SimpleYamlConfigLoader().load(cli.config);
                if (cli.format != null) {
                    config.outputFormat = cli.format;
                }
                if (cli.output != null) {
                    Path parent = cli.output.toAbsolutePath().getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                }
                if (cli.directOutput != null) {
                    if (cli.output == null) {
                        throw new IllegalArgumentException("--direct-output requires --output for the derived result");
                    }
                    Path parent = cli.directOutput.toAbsolutePath().getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                }
                if (cli.minConfidence != null) {
                    config.minConfidence = cli.minConfidence;
                }
                if (cli.parserMode != null) {
                    config.parserMode = cli.parserMode;
                }
                if (cli.grammarProfile != null) {
                    config.grammarProfile = cli.grammarProfile;
                }
                if (cli.databaseVersion != null) {
                    config.databaseVersion = cli.databaseVersion;
                    config.databaseVersionSource = "CONFIG";
                }
                if (cli.parallelism != null) {
                    config.executionParallelism = cli.parallelism;
                }
                AdaptorRegistry registry = AdaptorRegistry.load(cli.pluginDir);
                DatabaseAdaptor adaptor = registry.resolve(config.databaseType, config.adaptorId);
                var result = new ScanEngine().scan(config, adaptor);
                if (cli.directOutput != null && config.outputFormat != OutputFormat.JSON) {
                    throw new IllegalArgumentException("--direct-output is only available with JSON output");
                }
                JsonResultWriter jsonWriter = new JsonResultWriter();
                String rendered = config.outputFormat == OutputFormat.TABLE
                        ? new TableResultWriter().write(result)
                        : jsonWriter.write(result, config.includeEvidence, config.includeWarnings,
                                config.includeObservationCounts);
                if (cli.output == null) {
                    System.out.print(rendered);
                } else {
                    Files.writeString(cli.output, rendered);
                }
                if (cli.directOutput != null) {
                    Files.writeString(cli.directOutput, jsonWriter.writeDirect(result, config.includeEvidence,
                            config.includeWarnings, config.includeObservationCounts));
                }
                return ErrorCode.OK.code();
            } catch (IllegalArgumentException ex) {
                System.err.println("Configuration error: " + ex.getMessage());
                return ErrorCode.CONFIG_FORMAT_ERROR.code();
            } catch (AdaptorRegistry.AdaptorException ex) {
                System.err.println("Adaptor error: " + ex.getMessage());
                return ErrorCode.ADAPTOR_ERROR.code();
            } catch (Exception ex) {
                System.err.println("Scan failed: " + ex.getMessage());
                return ErrorCode.SCAN_RUNTIME_ERROR.code();
            }
        }

        private String help() {
            return """
                    relation-detector scan --config config.yml [--format json|table] [--output result.json] [--plugin-dir plugins]

                    Commands:
                      scan                 Run relationship detection.

                    Options:
                      --config <file>       YAML configuration file. Required.
                      --format <format>     json or table. Overrides output.format.
                      --output <file>       Write output to file. Defaults to stdout.
                      --direct-output <file> Write a direct-only JSON view beside --output.
                      --plugin-dir <dir>    Directory containing external adaptor jars.
                      --min-confidence <n>  Override output.minConfidence.
                      --parser-mode <mode>   auto, full-grammer, or token-event.
                      --grammar-profile <id> Override parser.grammarProfile.
                      --database-version <v> Override parser.databaseVersion.
                      --parallelism <n>       Parse independent file/object/log statements with up to n workers.
                      --help                Show this help.
                    """;
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
                case "auto", "full-grammer", "token-event" -> normalized;
                default -> throw new IllegalArgumentException(
                        "--parser-mode must be one of auto, full-grammer, token-event");
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
