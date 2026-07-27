package com.relationdetector.cli.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 从本次会话生成的小型 XML、日志、TSV 和 JSON 汇总构建性能报告；
 * 按文件逐个读取，不读取 sample-data 大结果。
 * EN: Builds the performance report from compact XML, log, TSV, and JSON artifacts created in the current session,
 * processing files one at a time and never reading large sample-data result payloads.
 */
final class PerformanceReportWriter {
    private static final String CLI_CASE = "case=";
    private static final String CLI_ELAPSED = " elapsedSeconds=";
    private static final String CLI_STATUS = " status=";
    private static final String SLOW_FIXTURE = "slow correctness fixture ";

    void write(Request request) {
        List<Path> mavenLogs = request.mavenLogs().stream().distinct().toList();
        ObjectNode report = ReleaseVerificationJson.MAPPER.createObjectNode();
        report.put("sessionStartEpoch", request.sessionStart());
        report.set("tests", tests(request.surefireRoot(), request.sessionStart()));
        report.putObject("fixtures").set("slowest", fixtureTimings(mavenLogs));
        report.set("cliCases", cliCases(request.cliLogRoot(), request.sessionStart()));
        report.set("cliBatch", batch(request.cliReport()));
        report.set("correctness", json(request.correctnessSummary()));
        report.set("canonicalFingerprints", fingerprints(request.fingerprints()));
        report.set("semanticFingerprints", fingerprints(request.semanticFingerprints()));
        report.set("maven", maven(mavenLogs));
        ReleaseVerificationJson.write(request.output(), report);
    }

    private ObjectNode tests(Path root, double sessionStart) {
        List<ObjectNode> suites = new ArrayList<>();
        int total = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        for (Path path : recent(root, "TEST-", ".xml", sessionStart)) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Element suite = factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                ObjectNode item = ReleaseVerificationJson.MAPPER.createObjectNode();
                item.put("name", attribute(suite, "name", stem(path)));
                item.put("seconds", decimal(suite, "time"));
                item.put("file", path.toString());
                suites.add(item);
                total += integer(suite, "tests");
                failures += integer(suite, "failures");
                errors += integer(suite, "errors");
                skipped += integer(suite, "skipped");
            } catch (Exception ignored) {
                // A partially written Surefire XML is not a usable timing sample.
            }
        }
        suites.sort(Comparator.<ObjectNode>comparingDouble(item -> -item.path("seconds").asDouble())
                .thenComparing(item -> item.path("name").asText()));
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        result.put("total", total);
        result.put("failures", failures);
        result.put("errors", errors);
        result.put("skipped", skipped);
        result.put("suiteCount", suites.size());
        ArrayNode slowest = result.putArray("slowest");
        suites.stream().limit(20).forEach(slowest::add);
        return result;
    }

    private ArrayNode cliCases(Path root, double sessionStart) {
        List<ObjectNode> cases = new ArrayList<>();
        for (Path path : recent(root, "", ".log", sessionStart)) {
            for (String line : lines(path)) {
                CliCaseTiming timing = cliCaseTiming(line);
                if (timing != null) {
                    ObjectNode item = ReleaseVerificationJson.MAPPER.createObjectNode();
                    item.put("name", timing.name());
                    item.put("elapsedSeconds", timing.elapsedSeconds());
                    item.put("status", timing.status());
                    item.put("file", path.toString());
                    cases.add(item);
                }
            }
        }
        cases.sort(Comparator.<ObjectNode>comparingInt(item -> -item.path("elapsedSeconds").asInt())
                .thenComparing(item -> item.path("name").asText()));
        ArrayNode result = ReleaseVerificationJson.MAPPER.createArrayNode();
        cases.forEach(result::add);
        return result;
    }

    private ObjectNode batch(Path path) {
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        JsonNode value = json(path);
        result.set("summary", value.path("summary").isObject()
                ? value.path("summary")
                : ReleaseVerificationJson.MAPPER.createObjectNode());
        List<ObjectNode> cases = new ArrayList<>();
        for (JsonNode source : value.path("cases")) {
            ObjectNode item = ReleaseVerificationJson.MAPPER.createObjectNode();
            item.put("name", source.path("id").asText(""));
            item.put("status", source.path("status").asText("UNKNOWN"));
            item.put("elapsedMillis", source.path("elapsedMillis").asLong());
            cases.add(item);
        }
        cases.sort(Comparator.<ObjectNode>comparingLong(item -> -item.path("elapsedMillis").asLong())
                .thenComparing(item -> item.path("name").asText()));
        ArrayNode output = result.putArray("cases");
        cases.forEach(output::add);
        if (path != null) {
            result.put("file", path.toString());
        }
        return result;
    }

    private ObjectNode fingerprints(Path path) {
        List<ObjectNode> items = new ArrayList<>();
        if (path != null && Files.isRegularFile(path)) {
            for (String line : lines(path)) {
                int separator = line.indexOf('\t');
                if (separator < 0 || line.isBlank()) {
                    continue;
                }
                ObjectNode item = ReleaseVerificationJson.MAPPER.createObjectNode();
                item.put("name", Path.of(line.substring(separator + 1)).getFileName().toString());
                item.put("sha256", line.substring(0, separator));
                items.add(item);
            }
        }
        items.sort(Comparator.comparing(item -> item.path("name").asText()));
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        result.put("count", items.size());
        ArrayNode values = result.putArray("items");
        items.forEach(values::add);
        return result;
    }

    private ObjectNode maven(List<Path> logs) {
        int grammarCount = 0;
        ArrayNode modules = ReleaseVerificationJson.MAPPER.createArrayNode();
        for (Path path : logs) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            for (String raw : lines(path)) {
                String line = stripAnsi(raw).stripTrailing();
                if (line.contains("Processing grammar:")) {
                    grammarCount++;
                }
                ReactorTiming timing = reactorTiming(line);
                if (timing != null) {
                    modules.addObject()
                            .put("name", timing.name())
                            .put("status", timing.status())
                            .put("elapsed", timing.elapsed());
                }
            }
        }
        ObjectNode result = ReleaseVerificationJson.MAPPER.createObjectNode();
        result.put("antlrGrammarProcessCount", grammarCount);
        result.set("modules", modules);
        return result;
    }

    private ArrayNode fixtureTimings(List<Path> logs) {
        List<ObjectNode> fixtures = new ArrayList<>();
        for (Path path : logs) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            for (String line : lines(path)) {
                FixtureTiming timing = fixtureTiming(line);
                if (timing != null) {
                    fixtures.add(ReleaseVerificationJson.MAPPER.createObjectNode()
                            .put("manifest", timing.manifest())
                            .put("elapsedMillis", timing.elapsedMillis())
                            .put("file", path.toString()));
                }
            }
        }
        fixtures.sort(Comparator.<ObjectNode>comparingInt(item -> -item.path("elapsedMillis").asInt())
                .thenComparing(item -> item.path("manifest").asText()));
        ArrayNode result = ReleaseVerificationJson.MAPPER.createArrayNode();
        fixtures.stream().limit(20).forEach(result::add);
        return result;
    }

    private CliCaseTiming cliCaseTiming(String line) {
        int caseStart = line.indexOf(CLI_CASE);
        int elapsedStart = caseStart < 0
                ? -1
                : line.indexOf(CLI_ELAPSED, caseStart + CLI_CASE.length());
        int statusStart = elapsedStart < 0
                ? -1
                : line.indexOf(CLI_STATUS, elapsedStart + CLI_ELAPSED.length());
        if (caseStart < 0 || elapsedStart < 0 || statusStart < 0) {
            return null;
        }
        String name = line.substring(caseStart + CLI_CASE.length(), elapsedStart);
        Integer elapsed = unsignedInteger(line.substring(
                elapsedStart + CLI_ELAPSED.length(), statusStart));
        int statusValueStart = statusStart + CLI_STATUS.length();
        int statusValueEnd = digitEnd(line, statusValueStart);
        Integer status = unsignedInteger(line.substring(statusValueStart, statusValueEnd));
        if (name.isBlank() || containsWhitespace(name) || elapsed == null || status == null) {
            return null;
        }
        return new CliCaseTiming(name, elapsed, status);
    }

    private ReactorTiming reactorTiming(String line) {
        if (!line.startsWith("[INFO] ") || !line.endsWith("]")) {
            return null;
        }
        String body = line.substring("[INFO] ".length());
        String successMarker = " SUCCESS [";
        String failureMarker = " FAILURE [";
        int success = body.lastIndexOf(successMarker);
        int failure = body.lastIndexOf(failureMarker);
        int marker = Math.max(success, failure);
        if (marker < 0) {
            return null;
        }
        String status = success > failure ? "SUCCESS" : "FAILURE";
        String name = trimReactorSeparator(body.substring(0, marker));
        String elapsed = body.substring(
                marker + (success > failure ? successMarker.length() : failureMarker.length()),
                body.length() - 1).strip();
        if (name.isBlank() || elapsed.isBlank()) {
            return null;
        }
        return new ReactorTiming(name, status, elapsed);
    }

    private FixtureTiming fixtureTiming(String raw) {
        String line = raw.stripTrailing();
        int marker = line.indexOf(SLOW_FIXTURE);
        if (marker < 0 || !line.endsWith(" ms")) {
            return null;
        }
        String body = line.substring(marker + SLOW_FIXTURE.length(), line.length() - " ms".length())
                .stripTrailing();
        OptionalInt separator = lastWhitespace(body);
        if (separator.isEmpty() || separator.getAsInt() < 1) {
            return null;
        }
        int separatorIndex = separator.getAsInt();
        String manifest = body.substring(0, separatorIndex).stripTrailing();
        Integer elapsed = unsignedInteger(body.substring(separatorIndex + 1).strip());
        return manifest.isBlank() || elapsed == null ? null : new FixtureTiming(manifest, elapsed);
    }

    private String stripAnsi(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            if (value.charAt(index) == '\u001B'
                    && index + 1 < value.length()
                    && value.charAt(index + 1) == '[') {
                int end = index + 2;
                while (end < value.length()
                        && (Character.isDigit(value.charAt(end)) || value.charAt(end) == ';')) {
                    end++;
                }
                if (end < value.length() && value.charAt(end) == 'm') {
                    index = end + 1;
                    continue;
                }
            }
            result.append(value.charAt(index));
            index++;
        }
        return result.toString();
    }

    private String trimReactorSeparator(String value) {
        int end = value.length();
        while (end > 0) {
            char current = value.charAt(end - 1);
            if (current != '.' && !Character.isWhitespace(current)) {
                break;
            }
            end--;
        }
        return value.substring(0, end).strip();
    }

    private int digitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return end;
    }

    private Integer unsignedInteger(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return null;
            }
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private OptionalInt lastWhitespace(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (Character.isWhitespace(value.charAt(index))) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private List<Path> recent(Path root, String prefix, String suffix, double sessionStart) {
        if (root == null || Files.notExists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .filter(path -> modified(path).toMillis() / 1000.0 >= sessionStart)
                    .toList();
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to list performance artifacts", error);
        }
    }

    private FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to inspect performance artifact", error);
        }
    }

    private List<String> lines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException error) {
            throw new ReleaseVerificationException("failed to read performance artifact", error);
        }
    }

    private JsonNode json(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return ReleaseVerificationJson.MAPPER.createObjectNode();
        }
        try {
            return ReleaseVerificationJson.MAPPER.readTree(path.toFile());
        } catch (IOException error) {
            return ReleaseVerificationJson.MAPPER.createObjectNode();
        }
    }

    private int integer(Element element, String name) {
        return Integer.parseInt(attribute(element, name, "0"));
    }

    private double decimal(Element element, String name) {
        return Double.parseDouble(attribute(element, name, "0"));
    }

    private String attribute(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    record Request(
            double sessionStart,
            Path surefireRoot,
            Path cliLogRoot,
            Path cliReport,
            Path correctnessSummary,
            Path fingerprints,
            Path semanticFingerprints,
            List<Path> mavenLogs,
            Path output
    ) {
        Request {
            mavenLogs = List.copyOf(mavenLogs);
        }
    }

    private record CliCaseTiming(String name, int elapsedSeconds, int status) {
    }

    private record ReactorTiming(String name, String status, String elapsed) {
    }

    private record FixtureTiming(String manifest, int elapsedMillis) {
    }
}
