package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 逐文件、逐顶层数组 item 校验 sample-data 结果矩阵，只保留当前 fact 及小型计数/引用索引；
 * 输出供 manifest 复用的小报告，禁止 manifest 再次物化大 JSON。
 * EN: Validates the sample-data result matrix one file and one top-level array item at a time, retaining only the
 * current fact and compact counters/reference indexes. Its small report is the sole integrity input to the manifest.
 */
final class SampleDataResultValidator {
    private static final String DERIVED_SUFFIX = "-derived-fresh";
    private static final List<String> FACT_SECTIONS = List.of(
            "relationships",
            "dataLineages",
            "namingEvidence",
            "derivedRelationships",
            "derivedDataLineages");
    private final SampleDataMetadataInventoryValidator inventoryValidator =
            new SampleDataMetadataInventoryValidator();
    private final SampleDataFactIntegrityValidator factValidator =
            new SampleDataFactIntegrityValidator();

    /**
     * CN: 校验结果目录中的完整 direct/derived 矩阵，并把跨 section 引用写入临时外存索引；
     * 成功输出有界 PASS 报告，覆盖、计数、路径、位置、引用或环校验失败时不生成 PASS。
     * EN: Validates the complete direct/derived result matrix while spilling cross-section references to temporary
     * disk indexes. It emits a bounded PASS report only when coverage, counts, paths, locations, refs, and cycles pass.
     */
    ObjectNode validate(Path resultDirectory, int expectedCategories, Path output) {
        Path workspace = output.toAbsolutePath().resolveSibling(
                ".result-validation-work-" + UUID.randomUUID());
        try {
            List<Path> paths = jsonFiles(resultDirectory);
            if (expectedCategories < 1 || paths.size() != expectedCategories * 2) {
                throw new ReleaseVerificationException("sample-data result JSON coverage mismatch");
            }
            Set<String> stems = new HashSet<>();
            for (Path path : paths) {
                stems.add(stem(path));
            }
            List<String> direct = stems.stream()
                    .filter(stem -> !stem.endsWith(DERIVED_SUFFIX))
                    .sorted()
                    .toList();
            if (direct.size() != expectedCategories) {
                throw new ReleaseVerificationException("sample-data direct category coverage mismatch");
            }
            for (String stem : direct) {
                if (!stems.contains(stem + DERIVED_SUFFIX)) {
                    throw new ReleaseVerificationException(
                            "sample-data derived result is missing for " + stem);
                }
            }
            int diagnostics = 0;
            long validatedSourceLocations = 0;
            Map<String, SampleDataMetadataInventoryValidator.InventoryValidation> inventories =
                    new LinkedHashMap<>();
            for (int index = 0; index < paths.size(); index++) {
                FileValidation validation = validateFile(
                        paths.get(index), workspace.resolve("file-" + index));
                diagnostics += validation.diagnostics();
                validatedSourceLocations += validation.validatedSourceLocations();
                String stem = stem(paths.get(index));
                String category = stem.endsWith(DERIVED_SUFFIX)
                        ? stem.substring(0, stem.length() - DERIVED_SUFFIX.length())
                        : stem;
                SampleDataMetadataInventoryValidator.InventoryValidation existing = inventories.putIfAbsent(
                        category, validation.inventory());
                if (existing != null && !existing.equals(validation.inventory())) {
                    throw failure(paths.get(index),
                            "direct and derived metadata inventories differ");
                }
            }
            if (diagnostics != 0) {
                throw new ReleaseVerificationException("sample-data results contain diagnostics");
            }
            ObjectNode report = ReleaseVerificationJson.MAPPER.createObjectNode();
            report.put("status", "PASS");
            report.put("categories", direct.size());
            report.put("jsonFiles", paths.size());
            report.put("diagnostics", diagnostics);
            report.put("validatedSourceLocationCount", validatedSourceLocations);
            report.put("completeDdlInventoryFiles", paths.size());
            report.putObject("integrity")
                    .put("evidenceRefs", "PASS")
                    .put("sourcePaths", "PASS")
                    .put("providedSourceLocationsValid", "PASS")
                    .put("rawObservationDuplicates", "PASS")
                    .put("derivedCycles", "PASS")
                    .put("metadataInventory", "PASS");
            ReleaseVerificationJson.write(output, report);
            return report;
        } finally {
            deleteRecursively(workspace);
        }
    }

    /**
     * CN: 流式读取单份sample-data JSON，只保留当前fact和有界计数/外存索引，返回该文件的诊断、位置和
     * inventory摘要；任何wire、引用、位置、重复observation或cycle违约均在返回前失败，且不写PASS报告。
     * EN: Streams one sample-data JSON while retaining only the current fact plus bounded counters and disk indexes,
     * then returns its diagnostic, location, and inventory summary. Any wire, reference, location, duplicate-observation,
     * or cycle violation fails before return and cannot produce a PASS report.
     */
    private FileValidation validateFile(Path path, Path workspace) {
        FileState state = new FileState(path, workspace);
        try (JsonParser parser = ReleaseVerificationJson.MAPPER.getFactory().createParser(path.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw failure(path, "result root must be an object");
            }
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token != JsonToken.FIELD_NAME) {
                    throw failure(path, "result field name is required");
                }
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) {
                    throw failure(path, "result field value is required");
                }
                if ("summary".equals(field)) {
                    state.summary = ReleaseVerificationJson.MAPPER.readTree(parser);
                } else if ("metadataInventory".equals(field)) {
                    if (state.inventory != null) {
                        throw failure(path, "metadataInventory must appear once");
                    }
                    state.inventory = inventoryValidator.validate(parser, value, path);
                } else if (arraySection(field) && value == JsonToken.START_ARRAY) {
                    readSection(parser, field, state);
                } else {
                    parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw failure(path, "result has trailing JSON content");
            }
        } catch (ReleaseVerificationException error) {
            throw error;
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to stream sample-data result: " + path, error);
        }
        state.finish();
        return new FileValidation(
                state.count("warnings"), state.validatedSourceLocations, state.inventory);
    }

    private boolean arraySection(String field) {
        return FACT_SECTIONS.contains(field)
                || "derivedNamingEvidence".equals(field)
                || "warnings".equals(field);
    }

    /**
     * CN: 逐项读取一个fact section，把单项完整性校验委托给fact validator，并只在当前文件state中累计
     * 有界计数和外存引用；遇到wire或闭包错误立即失败，不提交该文件的PASS状态。
     * EN: Streams one fact section, delegates per-item integrity to the fact validator, and retains only bounded
     * counters plus disk-backed references in the current file state. Wire or closure failures abort before PASS.
     */
    private void readSection(JsonParser parser, String section, FileState state) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw failure(state.path, "unterminated result array " + section);
            }
            JsonNode item = ReleaseVerificationJson.MAPPER.readTree(parser);
            if (item == null || !item.isObject()) {
                throw failure(state.path, section + " item must be an object");
            }
            state.increment(section);
            factValidator.validatePortableSources(state.path, item);
            state.validatedSourceLocations += factValidator.validateSourceLocations(
                    state.path, item, state.lineCounts);
            if ("derivedRelationships".equals(section) || "derivedDataLineages".equals(section)) {
                SampleDataFactIntegrityValidator.EvidenceSetStats stats =
                        factValidator.validateDerivedEvidenceSets(state.path, section, item);
                state.addEvidenceSets(section, stats);
            } else if (FACT_SECTIONS.contains(section)) {
                factValidator.validateRawObservations(state.path, section, item);
            }
            if ("namingEvidence".equals(section)) {
                String id = item.path("id").asText("");
                if (!id.isBlank()) {
                    state.namingIds.add(id);
                }
                if ("TRANSITIVE_NAMING_PATH".equals(item.path("rule").asText())) {
                    state.derivedNamingCount++;
                    state.addDerivedNamingEvidenceSets(
                            factValidator.validateDerivedNamingEvidenceSets(state.path, item));
                }
            } else if ("derivedNamingEvidence".equals(section)) {
                state.derivedNamingViewIds.add(item.path("id").asText(""));
            } else if ("relationships".equals(section) || "derivedRelationships".equals(section)) {
                factValidator.collectNamingReferences(state.path, section, item, state.namingReferences);
            }
            if ("derivedRelationships".equals(section) || "derivedDataLineages".equals(section)) {
                factValidator.validateDerivedCycle(state.path, item);
            }
        }
    }

    private List<Path> jsonFiles(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to list sample-data results", error);
        }
    }

    private String stem(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private ReleaseVerificationException failure(Path path, String message) {
        return new ReleaseVerificationException(path + ": " + message);
    }

    private void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new ReleaseVerificationException(
                            "failed to clean result validation workspace", error);
                }
            });
        } catch (IOException error) {
            throw new ReleaseVerificationException(
                    "failed to clean result validation workspace", error);
        }
    }

    private static final class FileState {
        private final Path path;
        private final Map<String, Integer> counts = new java.util.HashMap<>();
        private final Map<Path, Long> lineCounts = new java.util.HashMap<>();
        private final ExternalStringIndex namingIds;
        private final ExternalStringIndex namingReferences;
        private final ExternalStringIndex derivedNamingViewIds;
        private JsonNode summary;
        private SampleDataMetadataInventoryValidator.InventoryValidation inventory;
        private int derivedNamingCount;
        private long validatedSourceLocations;
        private int derivedRelationshipEvidenceSets;
        private int derivedDataLineageEvidenceSets;
        private int derivedNamingEvidenceSets;
        private BigInteger derivedRelationshipCombinations = BigInteger.ZERO;
        private BigInteger derivedDataLineageCombinations = BigInteger.ZERO;
        private BigInteger derivedNamingCombinations = BigInteger.ZERO;

        private FileState(Path path, Path workspace) {
            this.path = path;
            namingIds = new ExternalStringIndex(workspace.resolve("naming-ids"), 4_096);
            namingReferences = new ExternalStringIndex(workspace.resolve("naming-refs"), 4_096);
            derivedNamingViewIds =
                    new ExternalStringIndex(workspace.resolve("derived-naming-view"), 4_096);
        }

        private void increment(String section) {
            counts.merge(section, 1, Integer::sum);
        }

        private int count(String section) {
            return counts.getOrDefault(section, 0);
        }

        private void addEvidenceSets(
                String section,
                SampleDataFactIntegrityValidator.EvidenceSetStats stats
        ) {
            if ("derivedRelationships".equals(section)) {
                derivedRelationshipEvidenceSets += stats.setCount();
                derivedRelationshipCombinations = derivedRelationshipCombinations.add(stats.combinationCount());
            } else {
                derivedDataLineageEvidenceSets += stats.setCount();
                derivedDataLineageCombinations = derivedDataLineageCombinations.add(stats.combinationCount());
            }
        }

        private void addDerivedNamingEvidenceSets(
                SampleDataFactIntegrityValidator.EvidenceSetStats stats
        ) {
            derivedNamingEvidenceSets += stats.setCount();
            derivedNamingCombinations = derivedNamingCombinations.add(stats.combinationCount());
        }

        private void finish() {
            if (inventory == null) {
                throw new ReleaseVerificationException(
                        path + ": metadataInventory is required");
            }
            ExternalStringIndex.SortedIndex ids = namingIds.finish(true);
            ExternalStringIndex.SortedIndex references = namingReferences.finish(false);
            ExternalStringIndex.SortedIndex derivedView = derivedNamingViewIds.finish(true);
            if (!ExternalStringIndex.containsAll(ids, references)) {
                throw new ReleaseVerificationException(
                        path + ": unresolved NAMING_MATCH evidenceRef");
            }
            if (!ExternalStringIndex.containsAll(ids, derivedView)
                    || derivedView.size() != count("derivedNamingEvidence")) {
                throw new ReleaseVerificationException(
                        path + ": derived naming view is not closed");
            }
            Map<String, Integer> expected = new LinkedHashMap<>();
            expected.put("directRelationshipCount", count("relationships"));
            expected.put("derivedRelationshipCount", count("derivedRelationships"));
            expected.put("totalRelationshipCount",
                    count("relationships") + count("derivedRelationships"));
            expected.put("directDataLineageCount", count("dataLineages"));
            expected.put("derivedDataLineageCount", count("derivedDataLineages"));
            expected.put("totalDataLineageCount",
                    count("dataLineages") + count("derivedDataLineages"));
            expected.put("directNamingEvidenceCount",
                    count("namingEvidence") - derivedNamingCount);
            expected.put("derivedNamingEvidenceCount", derivedNamingCount);
            expected.put("totalNamingEvidenceCount", count("namingEvidence"));
            expected.put("warningCount", count("warnings"));
            JsonNode resolvedSummary = summary == null
                    ? ReleaseVerificationJson.MAPPER.createObjectNode()
                    : summary;
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                JsonNode actual = resolvedSummary.get(entry.getKey());
                if (actual == null) {
                    throw new ReleaseVerificationException(
                            path + ": summary." + entry.getKey() + " is required");
                }
                if (!actual.canConvertToInt() || actual.intValue() != entry.getValue()) {
                    throw new ReleaseVerificationException(
                            path + ": summary." + entry.getKey() + " does not match streamed count");
                }
            }
            validateEvidenceSetSummary(resolvedSummary);
            if (count("derivedNamingEvidence") != derivedNamingCount) {
                throw new ReleaseVerificationException(
                        path + ": derived naming view count mismatch");
            }
        }

        private void validateEvidenceSetSummary(JsonNode resolvedSummary) {
            if (!resolvedSummary.has("derivedRelationshipEvidenceSetCount")) {
                return;
            }
            requireSummary(resolvedSummary, "derivedRelationshipEvidenceSetCount",
                    BigInteger.valueOf(derivedRelationshipEvidenceSets));
            requireSummary(resolvedSummary, "derivedRelationshipSupportCombinationCount",
                    derivedRelationshipCombinations);
            requireSummary(resolvedSummary, "derivedDataLineageEvidenceSetCount",
                    BigInteger.valueOf(derivedDataLineageEvidenceSets));
            requireSummary(resolvedSummary, "derivedDataLineageSupportCombinationCount",
                    derivedDataLineageCombinations);
            requireSummary(resolvedSummary, "derivedNamingEvidenceSetCount",
                    BigInteger.valueOf(derivedNamingEvidenceSets));
            requireSummary(resolvedSummary, "derivedNamingSupportCombinationCount",
                    derivedNamingCombinations);
        }

        private void requireSummary(JsonNode summary, String field, BigInteger expected) {
            JsonNode actual = summary.get(field);
            if (actual == null || !actual.isIntegralNumber()
                    || !expected.equals(actual.bigIntegerValue())) {
                throw new ReleaseVerificationException(
                        path + ": summary." + field + " does not match streamed evidence sets");
            }
        }
    }

    private record FileValidation(
            int diagnostics,
            long validatedSourceLocations,
            SampleDataMetadataInventoryValidator.InventoryValidation inventory
    ) {
    }

}
