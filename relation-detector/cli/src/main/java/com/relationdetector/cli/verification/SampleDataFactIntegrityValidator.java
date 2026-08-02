package com.relationdetector.cli.verification;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 校验单个 sample-data fact 的可移植来源、文件位置、derived evidence-set、路径闭包和 naming 引用；
 * 输入是流式 reader 当前持有的一个 fact，输出仅包含有界计数，供结果文件编排器累计。本类不负责矩阵覆盖、
 * metadata inventory、跨文件配对或最终 summary 写入。
 *
 * <p>EN: Validates portable sources, file locations, derived evidence sets, path closure, and naming references for
 * one sample-data fact. It consumes only the fact currently held by the streaming reader and returns bounded counts
 * to the file orchestrator; it does not own matrix coverage, metadata inventory, cross-file pairing, or report output.
 */
final class SampleDataFactIntegrityValidator {
    private static final Set<String> DERIVED_HOP_KINDS = Set.of(
            "RELATIONSHIP", "LINEAGE", "NAMING", "TABLE_IDENTITY_BRIDGE");

    void collectNamingReferences(
            Path path,
            String section,
            JsonNode fact,
            ExternalStringIndex references
    ) {
        for (JsonNode evidence : fact.path("evidence")) {
            if (!"NAMING_MATCH".equals(evidence.path("type").asText())) {
                continue;
            }
            String reference = evidence.path("evidenceRef").asText("");
            if (reference.isBlank()) {
                reference = evidence.path("attributes").path("evidenceRef").asText("");
            }
            if (reference.isBlank()) {
                throw failure(path, "NAMING_MATCH evidenceRef is missing in " + section);
            }
            if (evidence.path("rawEvidence").size() != 0) {
                throw failure(path, "relationship NAMING_MATCH duplicates raw evidence");
            }
            references.add(reference);
        }
    }

    void validatePortableSources(Path path, JsonNode value) {
        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (("source".equals(field.getKey()) || "sourceFile".equals(field.getKey()))
                        && field.getValue().isTextual()
                        && Path.of(field.getValue().textValue()).isAbsolute()) {
                    throw failure(path, "result contains an absolute source path");
                }
                validatePortableSources(path, field.getValue());
            }
        } else if (value.isArray()) {
            value.forEach(child -> validatePortableSources(path, child));
        }
    }

    long validateSourceLocations(Path path, JsonNode value, Map<Path, Long> lineCounts) {
        long validated = 0;
        if (value.isObject()) {
            String sourceFile = value.path("sourceFile").asText("");
            JsonNode sourceLine = value.get("sourceLine");
            if (!sourceFile.isBlank() && sourceLine != null && sourceLine.canConvertToLong()) {
                Path source = Path.of(sourceFile);
                if (!Files.isRegularFile(source)) {
                    throw failure(path, "sourceFile does not exist: " + sourceFile);
                }
                long lines = lineCounts.computeIfAbsent(source, this::countLines);
                long line = sourceLine.longValue();
                if (line < 1 || line > lines) {
                    throw failure(path, "sourceLine is outside source file");
                }
                StatementSpan span = statementSpan(value.path("sourceStatementId").asText(""));
                if (span != null && (line < span.start() || line > span.end())) {
                    throw failure(path, "sourceLine is outside statement span");
                }
                validated++;
            }
            Iterator<JsonNode> children = value.elements();
            while (children.hasNext()) {
                validated += validateSourceLocations(path, children.next(), lineCounts);
            }
        } else if (value.isArray()) {
            for (JsonNode child : value) {
                validated += validateSourceLocations(path, child, lineCounts);
            }
        }
        return validated;
    }

    void validateRawObservations(Path path, String section, JsonNode fact) {
        Set<JsonNode> seen = new HashSet<>();
        for (JsonNode observation : fact.path("rawEvidence")) {
            if (!seen.add(observation.deepCopy())) {
                throw failure(path, "duplicate raw observation in " + section);
            }
        }
    }

    EvidenceSetStats validateDerivedEvidenceSets(Path path, String section, JsonNode fact) {
        if (fact.has("rawEvidence")) {
            throw failure(path, section + " must not contain rawEvidence");
        }
        return validateEvidenceSets(path, section, fact.path("path"), fact.path("evidenceSets"));
    }

    EvidenceSetStats validateDerivedNamingEvidenceSets(Path path, JsonNode fact) {
        JsonNode evidence = fact.path("evidence");
        if (!evidence.isArray() || evidence.isEmpty()) {
            throw failure(path, "derived naming evidence is missing its summary evidence");
        }
        JsonNode sets = evidence.get(0).path("attributes").path("evidenceSets");
        JsonNode syntheticPath = ReleaseVerificationJson.MAPPER.createArrayNode()
                .add(fact.path("source"))
                .add(fact.path("target"));
        return validateEvidenceSets(path, "derived naming evidence", syntheticPath, sets);
    }

    /**
     * CN: 复核 evidence-set 的线性大小、typed hop、闭合端点与组合乘积；失败不展开证据笛卡尔积。
     * EN: Checks linear evidence-set size, typed hops, endpoint closure, and support products without expanding a
     * Cartesian product of evidence observations.
     */
    private EvidenceSetStats validateEvidenceSets(
            Path path,
            String section,
            JsonNode factPath,
            JsonNode sets
    ) {
        if (!factPath.isArray() || factPath.size() < 2 || !sets.isArray() || sets.isEmpty()) {
            throw failure(path, section + " evidenceSets must be a non-empty array");
        }
        BigInteger combinations = BigInteger.ZERO;
        int setCount = 0;
        for (JsonNode set : sets) {
            JsonNode hops = set.path("hops");
            if (!hops.isArray() || hops.isEmpty()) {
                throw failure(path, section + " evidence set hops are required");
            }
            boolean completePath = factPath.size() > 2;
            if (completePath && hops.size() != factPath.size() - 1) {
                throw failure(path, section + " evidence hops do not match the fact path");
            }
            BigInteger product = BigInteger.ONE;
            int ordinal = 1;
            JsonNode firstSource = null;
            JsonNode lastTarget = null;
            JsonNode previousTarget = null;
            for (JsonNode hop : hops) {
                JsonNode hopOrdinal = hop.get("ordinal");
                if (hopOrdinal == null || !hopOrdinal.isIntegralNumber()
                        || hopOrdinal.intValue() != ordinal++) {
                    throw failure(path, section + " evidence hop ordinal is invalid");
                }
                if (!DERIVED_HOP_KINDS.contains(hop.path("kind").asText(""))) {
                    throw failure(path, section + " evidence hop kind is invalid");
                }
                JsonNode refs = hop.path("evidenceRefs");
                if (!refs.isArray() || refs.isEmpty()) {
                    throw failure(path, section + " evidence hop refs are required");
                }
                Set<String> unique = new HashSet<>();
                for (JsonNode ref : refs) {
                    if (!ref.isTextual() || ref.asText().isBlank() || !unique.add(ref.asText())) {
                        throw failure(path, section + " evidence hop refs must be unique non-blank strings");
                    }
                }
                if (firstSource == null) {
                    firstSource = hop.path("source");
                }
                if (completePath && (!hop.path("source").equals(factPath.get(ordinal - 2))
                        || !hop.path("target").equals(factPath.get(ordinal - 1)))) {
                    throw failure(path, section + " evidence hop does not match the fact path");
                }
                if (previousTarget != null && !previousTarget.equals(hop.path("source"))) {
                    throw failure(path, section + " evidence hop endpoints are not closed");
                }
                previousTarget = hop.path("target");
                lastTarget = previousTarget;
                product = product.multiply(BigInteger.valueOf(refs.size()));
            }
            if (!firstSource.equals(factPath.get(0))
                    || !lastTarget.equals(factPath.get(factPath.size() - 1))) {
                throw failure(path, section + " evidence set endpoints do not match the fact");
            }
            if (!set.path("combinationCount").isIntegralNumber()
                    || !product.equals(set.path("combinationCount").bigIntegerValue())) {
                throw failure(path, section + " evidence combination count is invalid");
            }
            combinations = combinations.add(product);
            setCount++;
        }
        return new EvidenceSetStats(setCount, combinations);
    }

    void validateDerivedCycle(Path path, JsonNode fact) {
        List<String> keys = new ArrayList<>();
        for (JsonNode endpoint : fact.path("path")) {
            keys.add(endpointKey(endpoint));
        }
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            int earlierLimit = Math.max(0, index - 1);
            if (keys.subList(0, earlierLimit).contains(key)) {
                throw failure(path, "derived path contains a cycle");
            }
        }
    }

    private String endpointKey(JsonNode endpoint) {
        if (!endpoint.isObject()) {
            return endpoint.asText();
        }
        List<String> parts = new ArrayList<>();
        for (String field : List.of("catalog", "schema", "table", "column")) {
            String value = endpoint.path(field).asText("");
            if (!value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(".", parts);
    }

    private StatementSpan statementSpan(String statementId) {
        int colon = statementId.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        int separator = statementId.indexOf('-', colon + 1);
        if (separator <= colon + 1 || separator >= statementId.length() - 1) {
            return null;
        }
        String start = statementId.substring(colon + 1, separator);
        String end = statementId.substring(separator + 1);
        if (!digits(start) || !digits(end)) {
            return null;
        }
        try {
            return new StatementSpan(Long.parseLong(start), Long.parseLong(end));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private boolean digits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private long countLines(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to count source lines", error);
        }
    }

    private ReleaseVerificationException failure(Path path, String message) {
        return new ReleaseVerificationException(path + ": " + message);
    }

    record EvidenceSetStats(int setCount, BigInteger combinationCount) {
    }

    private record StatementSpan(long start, long end) {
    }
}
