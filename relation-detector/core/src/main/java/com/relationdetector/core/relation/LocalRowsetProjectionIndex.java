package com.relationdetector.core.relation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.relationdetector.contracts.model.ColumnRef;

/**
 * Resolves uniquely copied physical columns behind procedure-local rowset columns.
 */
final class LocalRowsetProjectionIndex {
    private final UnaryOperator<String> normalize;
    private final Function<ColumnRef, String> identity;
    private final Map<String, List<Long>> declarations = new LinkedHashMap<>();
    private final Map<Key, List<Mapping>> mappings = new LinkedHashMap<>();

    LocalRowsetProjectionIndex(UnaryOperator<String> normalize, Function<ColumnRef, String> identity) {
        this.normalize = normalize;
        this.identity = identity;
    }

    void declare(String rowset, long line) {
        String key = normalize.apply(rowset);
        if (key.isBlank()) {
            return;
        }
        List<Long> lines = declarations.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!lines.contains(line)) {
            lines.add(line);
            lines.sort(Long::compareTo);
        }
    }

    void addPhysicalSource(String rowset, String column, ColumnRef source, long sourceLine) {
        if (!validTarget(rowset, column) || source == null) {
            return;
        }
        addMapping(rowset, column, new Mapping(source, null, sourceLine, false));
    }

    void addLocalSource(String rowset, String column, String sourceRowset, String sourceColumn, long sourceLine) {
        if (!validTarget(rowset, column) || !validTarget(sourceRowset, sourceColumn)) {
            return;
        }
        addMapping(rowset, column, new Mapping(null, key(sourceRowset, sourceColumn), sourceLine, false));
    }

    void block(String rowset, String column, long sourceLine) {
        if (!validTarget(rowset, column)) {
            return;
        }
        addMapping(rowset, column, new Mapping(null, null, sourceLine, true));
    }

    Optional<ResolvedSource> resolve(String rowset, String column, long useLine) {
        return resolve(key(rowset, column), useLine, new ArrayDeque<>());
    }

    private Optional<ResolvedSource> resolve(Key key, long useLine, Deque<Key> path) {
        if (path.contains(key)) {
            return Optional.empty();
        }
        path.addLast(key);
        try {
            long activeDeclaration = activeDeclaration(key.rowset(), useLine);
            List<Mapping> active = mappings.getOrDefault(key, List.of()).stream()
                    .filter(mapping -> mapping.sourceLine() >= activeDeclaration && mapping.sourceLine() <= useLine)
                    .toList();
            if (active.isEmpty() || active.stream().anyMatch(Mapping::blocked)) {
                return Optional.empty();
            }

            Map<String, ResolvedSource> unique = new LinkedHashMap<>();
            for (Mapping mapping : active) {
                Optional<ResolvedSource> resolved = mapping.physical() != null
                        ? Optional.of(new ResolvedSource(mapping.physical(), List.of(), mapping.sourceLine()))
                        : resolve(mapping.local(), mapping.sourceLine(), path);
                if (resolved.isEmpty()) {
                    return Optional.empty();
                }
                ResolvedSource source = resolved.orElseThrow();
                unique.putIfAbsent(identity.apply(source.column()), source);
            }
            if (unique.size() != 1) {
                return Optional.empty();
            }
            ResolvedSource source = unique.values().iterator().next();
            List<String> resolvedPath = new ArrayList<>();
            resolvedPath.add(key.display());
            resolvedPath.addAll(source.path());
            return Optional.of(new ResolvedSource(source.column(), resolvedPath, source.sourceLine()));
        } finally {
            path.removeLast();
        }
    }

    private long activeDeclaration(String rowset, long useLine) {
        long active = Long.MIN_VALUE;
        for (long declaration : declarations.getOrDefault(rowset, List.of())) {
            if (declaration <= useLine) {
                active = declaration;
            }
        }
        return active;
    }

    private void addMapping(String rowset, String column, Mapping mapping) {
        List<Mapping> values = mappings.computeIfAbsent(key(rowset, column), ignored -> new ArrayList<>());
        if (!values.contains(mapping)) {
            values.add(mapping);
            values.sort((left, right) -> Long.compare(left.sourceLine(), right.sourceLine()));
        }
    }

    private boolean validTarget(String rowset, String column) {
        return rowset != null && !rowset.isBlank() && column != null && !column.isBlank();
    }

    private Key key(String rowset, String column) {
        return new Key(normalize.apply(rowset), normalize.apply(column));
    }

    record ResolvedSource(ColumnRef column, List<String> path, long sourceLine) {
        ResolvedSource {
            path = List.copyOf(path);
        }
    }

    private record Key(String rowset, String column) {
        String display() {
            return rowset + "." + column;
        }
    }

    private record Mapping(ColumnRef physical, Key local, long sourceLine, boolean blocked) {
    }
}
