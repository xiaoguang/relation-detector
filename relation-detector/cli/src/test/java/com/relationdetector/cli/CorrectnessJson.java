package com.relationdetector.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class CorrectnessJson {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final DefaultPrettyPrinter PRETTY_PRINTER = new DefaultPrettyPrinter(
            Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
    private static final ObjectWriter WRITER = JSON.writer(PRETTY_PRINTER);

    private CorrectnessJson() {
    }

    static ExpectedRelations readRelations(Path file) {
        RelationsGolden value = read(file, RelationsGolden.class);
        return new ExpectedRelations(list(value.fingerprints()), list(value.forbiddenTables()));
    }

    static ExpectedRelations readRelations(String text, Path source) {
        RelationsGolden value = read(text, source, RelationsGolden.class);
        return new ExpectedRelations(list(value.fingerprints()), list(value.forbiddenTables()));
    }

    static ExpectedLineage readLineage(Path file) {
        LineageGolden value = read(file, LineageGolden.class);
        return new ExpectedLineage(true,
                list(value.fingerprints()),
                list(value.forbiddenSources()),
                list(value.forbiddenTargets()),
                map(value.warningCodes()));
    }

    static ExpectedLineage readLineage(String text, Path source) {
        LineageGolden value = read(text, source, LineageGolden.class);
        return new ExpectedLineage(true, list(value.fingerprints()), list(value.forbiddenSources()),
                list(value.forbiddenTargets()), map(value.warningCodes()));
    }

    static ExpectedNamingEvidence readNamingEvidence(Path file) {
        NamingEvidenceGolden value = read(file, NamingEvidenceGolden.class);
        return new ExpectedNamingEvidence(true, list(value.fingerprints()));
    }

    static ExpectedNamingEvidence readNamingEvidence(String text, Path source) {
        NamingEvidenceGolden value = read(text, source, NamingEvidenceGolden.class);
        return new ExpectedNamingEvidence(true, list(value.fingerprints()));
    }

    static ExpectedDiagnostics readDiagnostics(Path file) {
        DiagnosticsGolden value = read(file, DiagnosticsGolden.class);
        if (value.fixtureSha256() == null) {
            throw new IllegalArgumentException("Missing fixtureSha256 in " + file);
        }
        return new ExpectedDiagnostics(value.fixtureSha256(), map(value.warningCodes()));
    }

    static ExpectedDiagnostics readDiagnostics(String text, Path source) {
        DiagnosticsGolden value = read(text, source, DiagnosticsGolden.class);
        if (value.fixtureSha256() == null) {
            throw new IllegalArgumentException("Missing fixtureSha256 in " + source);
        }
        return new ExpectedDiagnostics(value.fixtureSha256(), map(value.warningCodes()));
    }

    static void writeRelations(Path file, ExpectedRelations value) throws Exception {
        write(file, new RelationsGolden(value.fingerprints(), value.forbiddenTables()));
    }

    static void writeLineage(Path file, ExpectedLineage value) throws Exception {
        write(file, new LineageGolden(value.fingerprints(), value.forbiddenSources(),
                value.forbiddenTargets(), value.warningCodes()));
    }

    static void writeNamingEvidence(Path file, ExpectedNamingEvidence value) throws Exception {
        write(file, new NamingEvidenceGolden(value.fingerprints()));
    }

    static void writeDiagnostics(Path file, ExpectedDiagnostics value) throws Exception {
        write(file, new DiagnosticsGolden(value.fixtureSha256(), value.warningCodes()));
    }

    private static <T> T read(Path file, Class<T> type) {
        try {
            return JSON.readValue(file.toFile(), type);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid correctness JSON " + file + ": " + error.getMessage(), error);
        }
    }

    private static <T> T read(String text, Path source, Class<T> type) {
        try {
            return JSON.readValue(text, type);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid correctness JSON " + source + ": " + error.getMessage(), error);
        }
    }

    private static void write(Path file, Object value) throws Exception {
        java.nio.file.Files.writeString(file, WRITER.writeValueAsString(value) + System.lineSeparator());
    }

    private static List<String> list(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static Map<String, Long> map(Map<String, Long> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private record RelationsGolden(List<String> fingerprints, List<String> forbiddenTables) {
    }

    private record LineageGolden(
            List<String> fingerprints,
            List<String> forbiddenSources,
            List<String> forbiddenTargets,
            Map<String, Long> warningCodes
    ) {
    }

    private record DiagnosticsGolden(String fixtureSha256, Map<String, Long> warningCodes) {
    }

    private record NamingEvidenceGolden(List<String> fingerprints) {
    }
}
