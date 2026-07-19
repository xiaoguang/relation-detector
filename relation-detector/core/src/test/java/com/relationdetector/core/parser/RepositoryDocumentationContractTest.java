package com.relationdetector.core.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RepositoryDocumentationContractTest {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]*]\\(([^)]+)\\)");
    private static final Pattern PENDING_FINGERPRINT = Pattern.compile("^- `([^`]+(?:->)[^`]+)`$",
            Pattern.MULTILINE);
    private static final Set<String> ALLOWED_DOC_ROOTS = Set.of("design", "guides", "parser-audit");
    private static final long MAX_REVIEWABLE_MARKDOWN_BYTES = 512L * 1024L;

    @Test
    void relativeMarkdownLinksResolveToRepositoryFiles() throws IOException {
        Path root = repoRoot();
        List<String> broken = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("docs"))) {
            files.filter(path -> path.toString().endsWith(".md")).forEach(file -> {
                try {
                    Matcher links = MARKDOWN_LINK.matcher(Files.readString(file));
                    while (links.find()) {
                        String target = normalizedTarget(links.group(1));
                        if (target.isBlank() || target.startsWith("#") || target.contains("://")
                                || target.startsWith("mailto:")) {
                            continue;
                        }
                        Path resolved = file.getParent().resolve(target).normalize();
                        if (!Files.exists(resolved)) {
                            broken.add(root.relativize(file) + " -> " + target);
                        }
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        assertTrue(broken.isEmpty(), "Broken relative Markdown links: " + broken);
    }

    @Test
    void operationalGuidesUseCurrentLocationsAndIsolatedRunners() throws IOException {
        Path root = repoRoot();
        String retiredGuideRoot = "docs/" + "relation-detector";
        assertFalse(Files.exists(root.resolve(retiredGuideRoot)));
        try (Stream<Path> files = Files.walk(root)) {
            List<String> oldReferences = files
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(retiredGuideRoot + "/");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(root::relativize)
                    .map(Path::toString)
                    .toList();
            assertTrue(oldReferences.isEmpty(), "Old documentation paths remain: " + oldReferences);
        }
        String buildGuide = Files.readString(root.resolve(
                "docs/guides/relation-detector/build-and-test-performance.md"));
        assertTrue(buildGuide.contains("run-correctness-isolated.sh"));
        assertTrue(buildGuide.contains("run-sample-data-isolated.sh"));
        assertTrue(buildGuide.contains(".mvn") && buildGuide.contains("忽略"));
        assertFalse(buildGuide.contains("单 JVM 执行全部"));
    }

    @Test
    void pendingLineageDoesNotRepeatExistingGoldenFingerprint() throws IOException {
        Path root = repoRoot();
        String pending = Files.readString(root.resolve("docs/parser-audit/data-lineage-pending-review.md"));
        StringBuilder expected = new StringBuilder();
        try (Stream<Path> files = Files.walk(root.resolve("relation-detector/test-fixtures/correctness"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().equals("expected-lineage.json"))
                    .toList()) {
                expected.append(Files.readString(file)).append('\n');
            }
        }
        List<String> duplicates = new ArrayList<>();
        Matcher fingerprints = PENDING_FINGERPRINT.matcher(pending);
        while (fingerprints.find()) {
            String fingerprint = fingerprints.group(1);
            if (expected.indexOf(fingerprint) >= 0) {
                duplicates.add(fingerprint);
            }
        }
        assertTrue(duplicates.isEmpty(), "Pending lineage already exists in golden: " + duplicates);
    }

    @Test
    void documentationTreeContainsOnlyCurrentReviewableSources() throws IOException {
        Path docs = repoRoot().resolve("docs");
        List<String> unexpectedRoots;
        try (Stream<Path> roots = Files.list(docs)) {
            unexpectedRoots = roots
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !ALLOWED_DOC_ROOTS.contains(name))
                    .sorted()
                    .toList();
        }

        List<String> oversized = new ArrayList<>();
        try (Stream<Path> files = Files.walk(docs)) {
            files.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> {
                        try {
                            return Files.size(path) > MAX_REVIEWABLE_MARKDOWN_BYTES;
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(repoRoot()::relativize)
                    .map(Path::toString)
                    .sorted()
                    .forEach(oversized::add);
        }

        assertTrue(unexpectedRoots.isEmpty(), "Unexpected docs roots: " + unexpectedRoots);
        assertTrue(oversized.isEmpty(), "Generated or oversized Markdown belongs in target artifacts: " + oversized);
    }

    @Test
    void parserAuditReadmeIndexesEveryCurrentAudit() throws IOException {
        Path auditRoot = repoRoot().resolve("docs/parser-audit");
        Path readme = auditRoot.resolve("README.md");
        assertTrue(Files.isRegularFile(readme), "docs/parser-audit/README.md must own the current audit inventory");

        String index = Files.readString(readme);
        Set<String> unlisted = new HashSet<>();
        Set<String> missingOwnership = new HashSet<>();
        try (Stream<Path> files = Files.list(auditRoot)) {
            List<String> auditNames = files.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.equals(readme))
                    .map(path -> path.getFileName().toString())
                    .toList();
            auditNames.stream()
                    .filter(name -> !index.contains("(" + name + ")"))
                    .forEach(unlisted::add);
            auditNames.stream()
                    .filter(name -> !hasOwnedAuditRow(index, name))
                    .forEach(missingOwnership::add);
        }
        List<String> nestedDirectories;
        try (Stream<Path> files = Files.list(auditRoot)) {
            nestedDirectories = files.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertTrue(unlisted.isEmpty(), "Current parser audits missing from README: " + unlisted);
        assertTrue(missingOwnership.isEmpty(),
                "Current parser audits require non-empty owner, input and refresh columns: " + missingOwnership);
        assertTrue(nestedDirectories.isEmpty(), "Historical parser-audit directories belong in Git history: "
                + nestedDirectories);
    }

    private boolean hasOwnedAuditRow(String index, String fileName) {
        return index.lines().anyMatch(line -> {
            if (!line.contains("(" + fileName + ")")) {
                return false;
            }
            String[] cells = line.split("\\|", -1);
            return cells.length >= 6
                    && !cells[2].isBlank()
                    && !cells[3].isBlank()
                    && !cells[4].isBlank();
        });
    }

    private String normalizedTarget(String rawTarget) {
        String target = rawTarget.strip();
        if (target.startsWith("<") && target.endsWith(">")) {
            target = target.substring(1, target.length() - 1);
        }
        int anchor = target.indexOf('#');
        if (anchor >= 0) {
            target = target.substring(0, anchor);
        }
        return target.replace("%20", " ");
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("docs"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return current;
    }
}
