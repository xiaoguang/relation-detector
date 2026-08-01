package com.relationdetector.semantic.extract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 对不展开进当前shard的typed审计引用生成有界摘要；输入是已由全局索引验证的稳定ID，
 * 输出为数量和长度分隔SHA-256。本类不决定引用归属，也不把摘要当作模型证据。
 * EN: Produces a bounded summary for typed audit references that are not expanded into the current shard.
 * It consumes stable IDs already validated by the global index and returns a count plus a length-delimited
 * SHA-256; it neither assigns ownership nor treats the digest as model evidence.
 */
final class SemanticExternalAuditReferences {
    private static final String SIDECAR_FILE_NAME = "external-audit-refs.tsv";
    private static final String PROJECTION_HEADER = "#semantic-external-audit-refs-v2";
    private static final List<ProjectionField> PROJECTION_FIELDS = List.of(
            new ProjectionField("evidenceRefs", "evidenceRefCount"),
            new ProjectionField("lineageRefs", "lineageRefCount"),
            new ProjectionField(
                    "supportingDerivedLineageRefs",
                    "supportingDerivedLineageRefCount"),
            new ProjectionField("relationshipRefs", "relationshipRefCount"));

    private SemanticExternalAuditReferences() {
    }

    static Path sidecar(Path bundlePath) {
        if (bundlePath == null || bundlePath.getParent() == null) {
            throw new IllegalArgumentException("semantic shard bundle path is required");
        }
        return bundlePath.getParent().resolve(SIDECAR_FILE_NAME);
    }

    static void write(Path path, Collection<String> references) throws IOException {
        Set<String> sorted = validated(references);
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            for (String reference : sorted) {
                writer.write(Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(reference.getBytes(StandardCharsets.UTF_8)));
                writer.newLine();
            }
        }
    }

    static Set<String> read(Path path) {
        return readProjection(path).externalReferences();
    }

    static ProjectionWriter projectionWriter(Path path, Collection<String> externalReferences) {
        return new ProjectionWriter(path, externalReferences);
    }

    static ProjectionIndex readProjection(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("semantic external audit sidecar path is required");
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null) {
                return new ProjectionIndex(Map.of(), Set.of());
            }
            if (!PROJECTION_HEADER.equals(first)) {
                Set<String> legacy = new LinkedHashSet<>();
                appendLegacy(legacy, first);
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLegacy(legacy, line);
                }
                return new ProjectionIndex(Map.of(), legacy);
            }
            Map<ProjectionKey, MutableProjection> projections = new LinkedHashMap<>();
            Set<String> external = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                appendProjectionRecord(projections, external, line);
            }
            Map<ProjectionKey, Projection> validated = new LinkedHashMap<>();
            projections.forEach((key, value) -> validated.put(key, value.validate()));
            return new ProjectionIndex(validated, external);
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "semantic external audit sidecar cannot be read");
        }
    }

    static ObjectNode project(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw new SemanticShardingException(
                    "semantic item must be an object before prompt projection");
        }
        ObjectNode result = (ObjectNode) document.deepCopy();
        for (ProjectionField field : PROJECTION_FIELDS) {
            Set<String> references = textValues(result.path(field.referenceField()));
            result.remove(field.referenceField());
            if (references.isEmpty()) {
                continue;
            }
            Snapshot snapshot = snapshot(references);
            result.put(field.countField(), snapshot.count());
            result.put(field.referenceField() + "Sha256", snapshot.sha256());
        }
        return result;
    }

    static Snapshot snapshot(Collection<String> references) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Set<String> sorted = validated(references);
            for (String reference : sorted) {
                byte[] bytes = reference.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return new Snapshot(sorted.size(), HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    static void appendSummary(ObjectNode context, Collection<String> references) {
        Snapshot snapshot = snapshot(references);
        context.put("externalAuditRefCount", snapshot.count());
        context.put("externalAuditRefsSha256", snapshot.sha256());
    }

    private static Set<String> validated(Collection<String> references) {
        if (references == null) {
            throw new IllegalArgumentException("semantic external audit references are required");
        }
        Set<String> sorted = new TreeSet<>();
        for (String reference : references) {
            if (reference == null || reference.isBlank()) {
                throw new SemanticExtractionValidationException(
                        "semantic external audit reference is invalid");
            }
            sorted.add(reference);
        }
        return sorted;
    }

    private static Set<String> textValues(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        if (!values.isArray()) {
            return result;
        }
        values.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank() || !result.add(value.asText())) {
                throw invalidSidecar();
            }
        });
        return result;
    }

    private static void appendLegacy(Set<String> result, String line) {
        if (line == null || line.isBlank()) {
            throw invalidSidecar();
        }
        String reference = decode(line);
        if (reference.isBlank() || !result.add(reference)) {
            throw invalidSidecar();
        }
    }

    private static void appendProjectionRecord(
            Map<ProjectionKey, MutableProjection> projections,
            Set<String> external,
            String line
    ) {
        String[] fields = line == null ? new String[0] : line.split("\\t", -1);
        if (fields.length == 2 && "E".equals(fields[0])) {
            String reference = decode(fields[1]);
            if (reference.isBlank() || !external.add(reference)) {
                throw invalidSidecar();
            }
            return;
        }
        if (fields.length == 5 && "F".equals(fields[0])) {
            ProjectionKey key = new ProjectionKey(decode(fields[1]), decode(fields[2]));
            int count;
            try {
                count = Integer.parseInt(fields[3]);
            } catch (NumberFormatException failure) {
                throw invalidSidecar();
            }
            if (count < 0 || !fields[4].matches("[0-9a-f]{64}")) {
                throw invalidSidecar();
            }
            MutableProjection previous = projections.putIfAbsent(
                    key, new MutableProjection(count, fields[4]));
            if (previous != null) {
                throw invalidSidecar();
            }
            return;
        }
        if (fields.length == 5 && "R".equals(fields[0])) {
            ProjectionKey key = new ProjectionKey(decode(fields[1]), decode(fields[2]));
            MutableProjection projection = projections.get(key);
            int ordinal;
            try {
                ordinal = Integer.parseInt(fields[3]);
            } catch (NumberFormatException failure) {
                throw invalidSidecar();
            }
            String reference = decode(fields[4]);
            if (projection == null || ordinal != projection.references.size()
                    || reference.isBlank()) {
                throw invalidSidecar();
            }
            projection.references.add(reference);
            return;
        }
        throw invalidSidecar();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw invalidSidecar();
        }
    }

    private static SemanticExtractionValidationException invalidSidecar() {
        return new SemanticExtractionValidationException(
                "semantic external audit sidecar contains an invalid record");
    }

    record Snapshot(int count, String sha256) {
        Snapshot {
            if (count < 0 || sha256 == null || sha256.length() != 64) {
                throw new IllegalArgumentException(
                        "semantic external audit reference summary is invalid");
            }
        }
    }

    static final class ProjectionWriter implements AutoCloseable {
        private final BufferedWriter writer;

        private ProjectionWriter(Path path, Collection<String> externalReferences) {
            if (path == null || externalReferences == null) {
                throw new IllegalArgumentException(
                        "semantic projection sidecar path and external references are required");
            }
            try {
                this.writer = Files.newBufferedWriter(
                        path,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
                writer.write(PROJECTION_HEADER);
                writer.newLine();
                for (String reference : validated(externalReferences)) {
                    writer.write("E\t" + encode(reference));
                    writer.newLine();
                }
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "semantic projection sidecar cannot be written");
            }
        }

        void append(JsonNode original) {
            if (original == null || !original.isObject()) {
                throw invalidSidecar();
            }
            String id = original.path("id").asText("");
            if (id.isBlank()) {
                throw invalidSidecar();
            }
            try {
                for (ProjectionField field : PROJECTION_FIELDS) {
                    JsonNode values = original.get(field.referenceField());
                    if (values == null) {
                        continue;
                    }
                    if (!values.isArray()) {
                        throw invalidSidecar();
                    }
                    List<String> references = new java.util.ArrayList<>();
                    values.forEach(value -> {
                        if (!value.isTextual() || value.asText().isBlank()) {
                            throw invalidSidecar();
                        }
                        references.add(value.asText());
                    });
                    Snapshot snapshot = snapshot(references);
                    writer.write("F\t" + encode(id) + "\t" + encode(field.referenceField())
                            + "\t" + snapshot.count() + "\t" + snapshot.sha256());
                    writer.newLine();
                    for (int ordinal = 0; ordinal < references.size(); ordinal++) {
                        writer.write("R\t" + encode(id) + "\t" + encode(field.referenceField())
                                + "\t" + ordinal + "\t" + encode(references.get(ordinal)));
                        writer.newLine();
                    }
                }
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "semantic projection sidecar cannot be written");
            }
        }

        @Override
        public void close() {
            try {
                writer.close();
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "semantic projection sidecar cannot be written");
            }
        }
    }

    static final class ProjectionIndex {
        private final Map<ProjectionKey, Projection> projections;
        private final Set<String> externalReferences;

        private ProjectionIndex(
                Map<ProjectionKey, Projection> projections,
                Collection<String> externalReferences
        ) {
            this.projections = Map.copyOf(projections);
            this.externalReferences = Set.copyOf(externalReferences);
        }

        Set<String> externalReferences() {
            return externalReferences;
        }

        ObjectNode restore(JsonNode projected) {
            if (projected == null || !projected.isObject()) {
                throw invalidSidecar();
            }
            ObjectNode result = (ObjectNode) projected.deepCopy();
            String id = result.path("id").asText("");
            if (id.isBlank()) {
                throw invalidSidecar();
            }
            for (ProjectionField field : PROJECTION_FIELDS) {
                Projection projection = projections.get(new ProjectionKey(id, field.referenceField()));
                result.remove(field.countField());
                result.remove(field.referenceField() + "Sha256");
                if (projection == null) {
                    continue;
                }
                ArrayNode values = result.putArray(field.referenceField());
                projection.references().forEach(values::add);
            }
            if (!StableSemanticId.canonicalJson(project(result))
                    .equals(StableSemanticId.canonicalJson(projected))) {
                throw invalidSidecar();
            }
            return result;
        }
    }

    private record ProjectionField(String referenceField, String countField) {
    }

    private record ProjectionKey(String itemId, String field) {
        private ProjectionKey {
            if (itemId == null || itemId.isBlank() || field == null || field.isBlank()) {
                throw invalidSidecar();
            }
        }
    }

    private record Projection(List<String> references) {
        private Projection {
            references = List.copyOf(references);
        }
    }

    private static final class MutableProjection {
        private final int count;
        private final String sha256;
        private final List<String> references = new java.util.ArrayList<>();

        private MutableProjection(int count, String sha256) {
            this.count = count;
            this.sha256 = sha256;
        }

        private Projection validate() {
            Snapshot actual = snapshot(references);
            if (actual.count() != count || !actual.sha256().equals(sha256)) {
                throw invalidSidecar();
            }
            return new Projection(references);
        }
    }
}
