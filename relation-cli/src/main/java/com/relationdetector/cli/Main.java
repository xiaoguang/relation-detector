package com.relationdetector.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.relationdetector.api.DatabaseAdaptor;
import com.relationdetector.api.Enums.ErrorCode;
import com.relationdetector.api.Enums.OutputFormat;
import com.relationdetector.core.DdlParserMode;
import com.relationdetector.core.JsonResultWriter;
import com.relationdetector.core.ScanEngine;
import com.relationdetector.core.SqlParserMode;
import com.relationdetector.core.TableResultWriter;

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
                if (cli.minConfidence != null) {
                    config.minConfidence = cli.minConfidence;
                }
                if (cli.sqlParserMode != null) {
                    config.sqlParserMode = cli.sqlParserMode;
                }
                if (cli.ddlParserMode != null) {
                    config.ddlParserMode = cli.ddlParserMode;
                }

                AdaptorRegistry registry = AdaptorRegistry.load(cli.pluginDir);
                DatabaseAdaptor adaptor = registry.resolve(config.databaseType, config.adaptorId);
                var result = new ScanEngine().scan(config, adaptor);
                String rendered = config.outputFormat == OutputFormat.TABLE
                        ? new TableResultWriter().write(result)
                        : new JsonResultWriter().write(result, config.includeEvidence, config.includeWarnings);
                if (cli.output == null) {
                    System.out.print(rendered);
                } else {
                    Files.writeString(cli.output, rendered);
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
                      --plugin-dir <dir>    Directory containing external adaptor jars.
                      --min-confidence <n>  Override output.minConfidence.
                      --sql-parser-mode <m>  simple, antlr-shadow, or antlr-primary. Overrides parser.sql.mode.
                      --ddl-parser-mode <m>  simple-ddl, antlr-ddl-shadow, or antlr-ddl-primary. Overrides parser.ddl.mode.
                      --help                Show this help.
                    """;
        }
    }

    static final class CliArguments {
        boolean help;
        Path config;
        OutputFormat format;
        Path output;
        Path pluginDir;
        Double minConfidence;
        SqlParserMode sqlParserMode;
        DdlParserMode ddlParserMode;

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
                    case "--plugin-dir" -> parsed.pluginDir = Path.of(requireValue(args, index++, arg));
                    case "--min-confidence" -> parsed.minConfidence = Double.parseDouble(requireValue(args, index++, arg));
                    case "--sql-parser-mode" -> parsed.sqlParserMode = SqlParserMode.fromConfig(requireValue(args, index++, arg));
                    case "--ddl-parser-mode" -> parsed.ddlParserMode = DdlParserMode.fromConfig(requireValue(args, index++, arg));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
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
