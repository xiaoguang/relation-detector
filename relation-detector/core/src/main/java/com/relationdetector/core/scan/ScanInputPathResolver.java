package com.relationdetector.core.scan;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves configured file inputs once, before scan capability validation. */
public final class ScanInputPathResolver {
    public List<Path> resolve(List<Path> files, List<Path> paths, List<String> includes, Path baseDirectory) {
        Path base = baseDirectory == null ? Path.of("") : baseDirectory;
        Set<Path> resolved = new LinkedHashSet<>();
        for (Path file : files == null ? List.<Path>of() : files) {
            resolved.add(requireRegularFile(resolve(file, base)));
        }

        List<Path> roots = paths == null ? List.of() : paths;
        List<String> patterns = includes == null || includes.isEmpty() ? List.of("**/*.sql") : includes;
        for (Path configuredRoot : roots) {
            Path root = resolve(configuredRoot, base);
            if (!Files.exists(root)) {
                throw new IllegalArgumentException("source path does not exist: " + root);
            }
            if (Files.isRegularFile(root)) {
                if (matchesAny(root.getFileName(), patterns)) {
                    resolved.add(requireRegularFile(root));
                }
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(file -> matchesAny(root.relativize(file), patterns))
                        .map(this::requireRegularFile)
                        .forEach(resolved::add);
            } catch (IOException ex) {
                throw new IllegalArgumentException("source path is unreadable: " + root, ex);
            }
        }
        if (!roots.isEmpty() && resolved.isEmpty()) {
            throw new IllegalArgumentException("source paths did not resolve to files: " + roots);
        }
        return resolved.stream().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private Path resolve(Path path, Path base) {
        if (path == null) {
            throw new IllegalArgumentException("source path is required");
        }
        Path candidate = path.isAbsolute() ? path : base.resolve(path);
        return candidate.toAbsolutePath().normalize();
    }

    private Path requireRegularFile(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("source file is unreadable: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("source file is unreadable: " + path, ex);
        }
    }

    private boolean matchesAny(Path relative, List<String> patterns) {
        for (String pattern : patterns) {
            if (matches(relative, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Path relative, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (matcher.matches(relative)) {
            return true;
        }
        return pattern.startsWith("**/")
                && FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3)).matches(relative);
    }
}
