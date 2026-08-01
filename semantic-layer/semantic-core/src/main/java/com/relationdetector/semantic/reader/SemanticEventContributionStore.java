package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventCandidateMerger;

/**
 * CN: 将同一typed event的标量、加权confidence和集合成员分别落盘并外排归并；输入是有界运输窗口
 * contribution，输出只能通过预算门禁物化的单个candidate。本类不关联relationship、不推断SQL结构，
 * 也不在堆内累积跨窗口成员。
 * EN: Spools scalar identity, weighted confidence, and set members of one typed event separately and merges them
 * through external sorting. It accepts bounded transport-window contributions and materializes one candidate only
 * through the input-budget gate; it neither associates relationships nor infers SQL or accumulates cross-window
 * members on heap.
 */
final class SemanticEventContributionStore implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MATERIALIZATION_MARGIN_BYTES = 1_024L;

    private final Path workspace;
    private final Path contributionsRaw;
    private final Path membersRaw;
    private final BufferedWriter contributionWriter;
    private final BufferedWriter memberWriter;
    private ExternalJsonRecordStore descriptors;
    private Path uniqueMembers;
    private boolean finished;
    private boolean closed;

    SemanticEventContributionStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("semantic event contribution workspace is required");
        }
        this.workspace = workspace;
        try {
            Files.createDirectories(workspace);
            contributionsRaw = workspace.resolve("contributions.raw");
            membersRaw = workspace.resolve("members.raw");
            contributionWriter = writer(contributionsRaw);
            memberWriter = writer(membersRaw);
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to create semantic event contribution store", failure);
        }
    }

    void append(SemanticEventCandidate candidate) {
        ensureWritable();
        if (candidate == null) {
            throw new IllegalArgumentException("semantic event contribution is required");
        }
        if (!candidate.supportingDerivedLineageRefs().isEmpty()
                || !candidate.relationshipRefs().isEmpty()) {
            throw new ScanResultContractException(
                    "semantic event transport contribution contains global associations");
        }
        int count = directLineageCount(candidate);
        try {
            contributionWriter.write(encode(candidate.id()));
            contributionWriter.write('\t');
            contributionWriter.write(encode(JSON.writeValueAsString(candidate)));
            contributionWriter.write('\t');
            contributionWriter.write(encode(JSON.writeValueAsString(identity(candidate))));
            contributionWriter.write('\t');
            contributionWriter.write(Integer.toString(count));
            contributionWriter.write('\t');
            contributionWriter.write(candidate.confidence().toPlainString());
            contributionWriter.newLine();
            appendMembers(candidate.id(), MemberKind.OPERATION, candidate.operationKinds());
            appendMembers(candidate.id(), MemberKind.INPUT, candidate.inputEndpoints());
            appendMembers(candidate.id(), MemberKind.OUTPUT, candidate.outputEndpoints());
            appendMembers(candidate.id(), MemberKind.LINEAGE, candidate.lineageRefs());
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to append semantic event contribution", failure);
        }
    }

    void finish() {
        ensureOpen();
        if (finished) {
            return;
        }
        try {
            contributionWriter.close();
            memberWriter.close();
            descriptors = new ExternalJsonRecordStore(
                    workspace.resolve("descriptors"), this::mergeDescriptor);
            aggregateContributions();
            aggregateMembers();
            descriptors.finish();
            finished = true;
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to finish semantic event contribution store", failure);
        }
    }

    void forEachEventId(Consumer<String> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("semantic event consumer is required");
        }
        finish();
        descriptors.forEachDescriptor((eventId, ignored) -> consumer.accept(eventId));
    }

    SemanticEventCandidate materializeWithinBudget(
            String eventId,
            long additionalEstimatedBytes,
            int maxInputTokens
    ) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("semantic event id is required");
        }
        finish();
        JsonNode descriptor = descriptors.get(eventId)
                .map(ExternalJsonRecordStore.Record::value)
                .orElseThrow(() -> new ScanResultContractException(
                        "semantic event contribution descriptor is missing"));
        SemanticEventInputBudget.requireWithin(
                descriptor.path("estimatedBaseBytes").asLong(-1),
                additionalEstimatedBytes,
                maxInputTokens);
        return materialize(eventId, descriptor);
    }

    private void aggregateContributions() throws IOException {
        Path sorted = workspace.resolve("contributions.sorted");
        new ExternalLineSorter().sort(
                contributionsRaw, sorted, workspace.resolve("contribution-sort"));
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
            ContributionAccumulator current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = split(line, 5);
                if (current == null || !current.encodedEventId.equals(fields[0])) {
                    if (current != null) {
                        appendContributionDescriptor(current);
                    }
                    current = new ContributionAccumulator(fields[0], fields[2]);
                } else if (!current.encodedIdentity.equals(fields[2])) {
                    throw new ScanResultContractException(
                            "semantic event contributions disagree on typed identity");
                }
                current.add(fields[1], fields[3], fields[4]);
            }
            if (current != null) {
                appendContributionDescriptor(current);
            }
        }
    }

    private void appendContributionDescriptor(ContributionAccumulator contribution)
            throws IOException {
        ObjectNode descriptor = JSON.createObjectNode();
        JsonNode identity = JSON.readTree(decode(contribution.encodedIdentity));
        descriptor.set("identity", identity);
        descriptor.put("directLineageCount", contribution.count);
        descriptor.put("confidence", contribution.confidence.toPlainString());
        long scalarBytes = JSON.writeValueAsBytes(identity).length + MATERIALIZATION_MARGIN_BYTES;
        descriptor.put("estimatedBaseBytes", scalarBytes);
        descriptors.append(decode(contribution.encodedEventId), descriptor);
    }

    private void aggregateMembers() throws IOException {
        Path sorted = workspace.resolve("members.sorted");
        new ExternalLineSorter().sort(membersRaw, sorted, workspace.resolve("member-sort"));
        uniqueMembers = workspace.resolve("members.unique");
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             RandomAccessFile output = new RandomAccessFile(uniqueMembers.toFile(), "rw")) {
            String currentEvent = null;
            long offset = 0;
            long estimatedBytes = 0;
            String previous = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(previous)) {
                    continue;
                }
                String[] fields = split(line, 3);
                if (!fields[0].equals(currentEvent)) {
                    if (currentEvent != null) {
                        appendMemberDescriptor(currentEvent, offset, estimatedBytes);
                    }
                    currentEvent = fields[0];
                    offset = output.getFilePointer();
                    estimatedBytes = 0;
                }
                output.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
                String value = decode(fields[2]);
                long encodedValueBytes = JSON.writeValueAsBytes(value).length + 1L;
                estimatedBytes += MemberKind.fromCode(fields[1]).serializedOccurrences()
                        * encodedValueBytes;
                previous = line;
            }
            if (currentEvent != null) {
                appendMemberDescriptor(currentEvent, offset, estimatedBytes);
            }
        }
    }

    private void appendMemberDescriptor(
            String encodedEventId,
            long offset,
            long estimatedBytes
    ) {
        ObjectNode descriptor = JSON.createObjectNode();
        descriptor.put("memberOffset", offset);
        descriptor.put("estimatedBaseBytes", estimatedBytes);
        descriptors.append(decode(encodedEventId), descriptor);
    }

    private JsonNode mergeDescriptor(JsonNode left, JsonNode right) {
        ObjectNode merged = left.deepCopy();
        right.fields().forEachRemaining(field -> {
            JsonNode existing = merged.get(field.getKey());
            if (existing == null) {
                merged.set(field.getKey(), field.getValue());
            } else if ("estimatedBaseBytes".equals(field.getKey())) {
                merged.put(field.getKey(), Math.addExact(existing.asLong(), field.getValue().asLong()));
            } else if (!existing.equals(field.getValue())) {
                throw new ScanResultContractException(
                        "semantic event contribution descriptor is inconsistent");
            }
        });
        return merged;
    }

    private SemanticEventCandidate materialize(String eventId, JsonNode descriptor) {
        List<String> operations = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        List<String> lineage = new ArrayList<>();
        readMembers(eventId, descriptor.path("memberOffset").asLong(-1),
                operations, inputs, outputs, lineage);
        operations.sort(String::compareTo);
        inputs.sort(String::compareTo);
        outputs.sort(String::compareTo);
        lineage.sort(String::compareTo);
        int count = Math.toIntExact(descriptor.path("directLineageCount").asLong());
        BigDecimal confidence = new BigDecimal(descriptor.path("confidence").asText());
        JsonNode identity = descriptor.path("identity");
        SemanticEventCandidate candidate = new SemanticEventCandidate(
                eventId,
                identity.path("eventKind").asText(),
                identity.path("sourceType").asText(),
                identity.path("sourceObject").asText(),
                identity.path("sourceObjectType").asText(),
                identity.path("sourceObjectName").asText(),
                identity.path("sourceFile").asText(),
                identity.path("sourceStatementId").asText(),
                "",
                "",
                "",
                operations,
                inputs,
                outputs,
                lineage,
                List.of(),
                List.of(),
                lineage,
                confidence,
                Map.of("directLineageCount", count));
        return new SemanticEventCandidateMerger().normalize(candidate);
    }

    private void readMembers(
            String eventId,
            long offset,
            List<String> operations,
            List<String> inputs,
            List<String> outputs,
            List<String> lineage
    ) {
        if (offset < 0) {
            return;
        }
        String encodedEventId = encode(eventId);
        try (RandomAccessFile file = new RandomAccessFile(uniqueMembers.toFile(), "r")) {
            file.seek(offset);
            String line;
            while ((line = file.readLine()) != null) {
                String[] fields = split(line, 3);
                if (!encodedEventId.equals(fields[0])) {
                    break;
                }
                List<String> target = switch (MemberKind.fromCode(fields[1])) {
                    case OPERATION -> operations;
                    case INPUT -> inputs;
                    case OUTPUT -> outputs;
                    case LINEAGE -> lineage;
                };
                target.add(decode(fields[2]));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to materialize semantic event contribution", failure);
        }
    }

    private ObjectNode identity(SemanticEventCandidate candidate) {
        ObjectNode identity = JSON.createObjectNode();
        identity.put("eventKind", candidate.eventKind());
        identity.put("sourceType", candidate.sourceType());
        identity.put("sourceObject", candidate.sourceObject());
        identity.put("sourceObjectType", candidate.sourceObjectType());
        identity.put("sourceObjectName", candidate.sourceObjectName());
        identity.put("sourceFile", candidate.sourceFile());
        identity.put("sourceStatementId", candidate.sourceStatementId());
        return identity;
    }

    private int directLineageCount(SemanticEventCandidate candidate) {
        Object value = candidate.attributes().get("directLineageCount");
        return value instanceof Number number && number.intValue() > 0
                ? number.intValue()
                : 1;
    }

    private void appendMembers(String eventId, MemberKind kind, List<String> values)
            throws IOException {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ScanResultContractException(
                        "semantic event contribution member is blank");
            }
            memberWriter.write(encode(eventId));
            memberWriter.write('\t');
            memberWriter.write(kind.code());
            memberWriter.write('\t');
            memberWriter.write(encode(value));
            memberWriter.newLine();
        }
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private String[] split(String line, int expected) {
        String[] fields = line.split("\\t", -1);
        if (fields.length != expected) {
            throw new ScanResultContractException(
                    "semantic event contribution record is malformed");
        }
        return fields;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void ensureWritable() {
        ensureOpen();
        if (finished) {
            throw new IllegalStateException(
                    "semantic event contribution store is already finished");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic event contribution store is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!finished) {
                contributionWriter.close();
                memberWriter.close();
            }
            if (descriptors != null) {
                descriptors.close();
            }
            SemanticInputStore.deleteRecursively(workspace);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "failed to clean semantic event contribution store", failure);
        }
    }

    private static final class ContributionAccumulator {
        private final String encodedEventId;
        private final String encodedIdentity;
        private String previousSortKey;
        private long count;
        private BigDecimal confidence;

        private ContributionAccumulator(String encodedEventId, String encodedIdentity) {
            this.encodedEventId = encodedEventId;
            this.encodedIdentity = encodedIdentity;
        }

        private void add(String sortKey, String countValue, String confidenceValue) {
            if (sortKey.equals(previousSortKey)) {
                return;
            }
            try {
                long contributionCount = Long.parseLong(countValue);
                if (contributionCount <= 0) {
                    throw new NumberFormatException("non-positive count");
                }
                BigDecimal contributionConfidence = new BigDecimal(confidenceValue);
                long combinedCount = Math.addExact(count, contributionCount);
                confidence = confidence == null
                        ? contributionConfidence
                        : confidence.multiply(BigDecimal.valueOf(count))
                                .add(contributionConfidence.multiply(
                                        BigDecimal.valueOf(contributionCount)))
                                .divide(
                                        BigDecimal.valueOf(combinedCount),
                                        4,
                                        RoundingMode.HALF_UP);
                count = combinedCount;
                previousSortKey = sortKey;
                if (combinedCount > Integer.MAX_VALUE) {
                    throw new ArithmeticException("event contribution count overflow");
                }
            } catch (NumberFormatException | ArithmeticException failure) {
                throw new ScanResultContractException(
                        "semantic event contribution aggregate is invalid");
            }
        }
    }

    private enum MemberKind {
        OPERATION("O", 1L),
        INPUT("I", 2L),
        OUTPUT("T", 2L),
        LINEAGE("L", 2L);

        private final String code;
        private final long serializedOccurrences;

        MemberKind(String code, long serializedOccurrences) {
            this.code = code;
            this.serializedOccurrences = serializedOccurrences;
        }

        private String code() {
            return code;
        }

        private long serializedOccurrences() {
            return serializedOccurrences;
        }

        private static MemberKind fromCode(String code) {
            for (MemberKind kind : values()) {
                if (kind.code.equals(code)) {
                    return kind;
                }
            }
            throw new ScanResultContractException(
                    "semantic event contribution member kind is invalid");
        }
    }
}
