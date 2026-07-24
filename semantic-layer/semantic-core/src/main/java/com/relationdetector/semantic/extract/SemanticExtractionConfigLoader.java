package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * CN: 从 YAML/JSON transport 读取 semantic extraction fields 并构造 typed config；文件/结构错误明确失败，不执行 scan、LLM 或 artifact writing。
 * EN: Reads semantic extraction fields from YAML or JSON transport and creates typed configuration. File or shape errors fail explicitly; no scan, LLM call, or artifact writing occurs here.
 */
public final class SemanticExtractionConfigLoader {
    private static final YAMLMapper YAML = new YAMLMapper();
    private static final Set<String> ROOT_FIELDS = Set.of("semanticExtraction");
    private static final Set<String> EXTRACTION_FIELDS = Set.of(
            "provider", "inputs", "input", "output", "focus", "model",
            "reasoningEffort", "reasoning-effort",
            "maxOutputTokens", "max-output-tokens",
            "baseUrl", "base-url",
            "apiKeyEnv", "api-key-env",
            "maxRelationships", "max-relationships",
            "maxLineage", "max-lineage",
            "maxNamingEvidence", "max-naming",
            "requestOnly", "request-only",
            "artifactRetention", "artifact-retention",
            "sharding",
            "shardMaxOutputTokens", "reconciliationMaxOutputTokens",
            "requestTimeoutSeconds", "maxTransportRetries");
    private static final Set<String> SHARDING_FIELDS = Set.of(
            "mode", "targetInputTokens", "maxInputTokens", "maxShardCount", "reconcile");

    public SemanticExtractionConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("semantic extraction config file does not exist: " + path);
        }
        Path configPath = path.toAbsolutePath().normalize();
        try {
            JsonNode root = YAML.readTree(configPath.toFile());
            return from(root, configPath.getParent());
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read semantic extraction config: " + path, e);
        }
    }

    public SemanticExtractionConfig from(JsonNode root) {
        return from(root, null);
    }

    /**
     * CN: 校验root、semanticExtraction和sharding的完整字段集合，并相对配置目录解析输入/输出路径后一次性
     * 构造typed config；不读取bundle或调用模型，任一未知字段、类型、数值或路径文本错误都在返回前失败。
     *
     * EN: Validates the complete root, semanticExtraction, and sharding field sets and resolves input/output paths
     * against the configuration directory before creating one typed config. Unknown fields, invalid shapes, numeric
     * values, or path text fail before any bundle read or model invocation.
     */
    private SemanticExtractionConfig from(JsonNode root, Path configDirectory) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("semantic extraction config root must be an object");
        }
        rejectUnknownFields(root, ROOT_FIELDS, "semantic extraction config root");
        JsonNode extract = root.get("semanticExtraction");
        if (extract == null || !extract.isObject()) {
            throw new IllegalArgumentException("semanticExtraction must be an object");
        }
        rejectUnknownFields(extract, EXTRACTION_FIELDS, "semanticExtraction");

        List<Path> inputs = new ArrayList<>();
        JsonNode inputNode = extract.get("inputs");
        if (inputNode != null) {
            if (!inputNode.isArray()) {
                throw new IllegalArgumentException("semanticExtraction.inputs must be an array");
            }
            for (JsonNode item : inputNode) {
                if (!item.isTextual() || item.textValue().isBlank()) {
                    throw new IllegalArgumentException(
                            "semanticExtraction.inputs entries must be non-blank strings");
                }
                inputs.add(resolvePath(item.textValue(), configDirectory));
            }
        }
        addPath(inputs, text(extract, "input", null, ""), configDirectory);
        Path output = pathOrNull(text(extract, "output", null, ""), configDirectory);

        JsonNode sharding = extract.get("sharding");
        if (sharding != null && !sharding.isObject()) {
            throw new IllegalArgumentException("semanticExtraction.sharding must be an object");
        }
        if (sharding != null) {
            rejectUnknownFields(sharding, SHARDING_FIELDS, "semanticExtraction.sharding");
        }
        return new SemanticExtractionConfig(
                text(extract, "provider", null, "codex-session"),
                inputs,
                output,
                text(extract, "focus", null, ""),
                text(extract, "model", null, "gpt-5.6-sol"),
                text(extract, "reasoningEffort", "reasoning-effort", "xhigh"),
                integer(extract, "maxOutputTokens", "max-output-tokens", 12000),
                text(extract, "baseUrl", "base-url", "https://api.openai.com/v1"),
                text(extract, "apiKeyEnv", "api-key-env", "OPENAI_API_KEY"),
                integer(extract, "maxRelationships", "max-relationships", 0),
                integer(extract, "maxLineage", "max-lineage", 0),
                integer(extract, "maxNamingEvidence", "max-naming", 0),
                bool(extract, "requestOnly", "request-only", false),
                ArtifactRetention.parse(text(extract, "artifactRetention", "artifact-retention", "full")),
                new SemanticShardingOptions(
                        SemanticShardMode.parse(text(sharding, "mode", null, "auto")),
                        integer(sharding, "targetInputTokens", null, 240000),
                        integer(sharding, "maxInputTokens", null, 800000),
                        integer(sharding, "maxShardCount", null, 128),
                        bool(sharding, "reconcile", null, true)),
                integer(extract, "shardMaxOutputTokens", null, 24000),
                integer(extract, "reconciliationMaxOutputTokens", null, 16000),
                integer(extract, "requestTimeoutSeconds", null, 900),
                integer(extract, "maxTransportRetries", null, 2));
    }

    private void addPath(List<Path> paths, String value, Path configDirectory) {
        if (value != null && !value.isBlank()) {
            paths.add(resolvePath(value, configDirectory));
        }
    }

    private Path pathOrNull(String value, Path configDirectory) {
        return value == null || value.isBlank() ? null : resolvePath(value, configDirectory);
    }

    private Path resolvePath(String value, Path configDirectory) {
        Path resolved = Path.of(value);
        if (!resolved.isAbsolute() && configDirectory != null) {
            resolved = configDirectory.resolve(resolved);
        }
        return resolved.normalize();
    }

    private String text(JsonNode object, String name, String alias, String defaultValue) {
        JsonNode value = value(object, name, alias);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(fieldLabel(name) + " must be a string");
        }
        return value.textValue();
    }

    private int integer(JsonNode object, String name, String alias, int defaultValue) {
        JsonNode value = value(object, name, alias);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(fieldLabel(name) + " must be an integer");
        }
        return value.intValue();
    }

    private boolean bool(JsonNode object, String name, String alias, boolean defaultValue) {
        JsonNode value = value(object, name, alias);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(fieldLabel(name) + " must be a boolean");
        }
        return value.booleanValue();
    }

    private JsonNode value(JsonNode object, String name, String alias) {
        if (object == null) {
            return null;
        }
        boolean hasName = object.has(name);
        boolean hasAlias = alias != null && object.has(alias);
        if (hasName && hasAlias) {
            throw new IllegalArgumentException(fieldLabel(name) + " is configured more than once");
        }
        return hasName ? object.get(name) : hasAlias ? object.get(alias) : null;
    }

    private String fieldLabel(String name) {
        return "semantic extraction field " + name;
    }

    private void rejectUnknownFields(JsonNode object, Set<String> allowed, String label) {
        object.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(label + " has unknown field: " + name);
            }
        });
    }
}
