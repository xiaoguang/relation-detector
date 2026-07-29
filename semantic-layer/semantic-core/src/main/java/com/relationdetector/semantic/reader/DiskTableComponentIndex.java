package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CN: 从外排的encoded table与typed edge文件构建磁盘connected-component索引；输入文件由owner planner
 * 生成，输出稳定component root查询，不加载table数量级的堆数组，也不解释fact语义。
 * EN: Builds a disk-backed connected-component index from externally spooled encoded tables and typed edges. It
 * exposes stable component-root lookup without a heap array proportional to table count and never interprets facts.
 */
public final class DiskTableComponentIndex implements AutoCloseable {
    private final DiskStringDictionary dictionary;
    private final DiskUnionFind components;

    public DiskTableComponentIndex(Path rawTables, Path rawEdges, Path workspace) {
        if (rawTables == null || rawEdges == null || workspace == null) {
            throw new IllegalArgumentException("table, edge, and component workspace paths are required");
        }
        try {
            Files.createDirectories(workspace);
            dictionary = DiskStringDictionary.build(
                    rawTables, workspace.resolve("tables.dictionary"), workspace.resolve("dictionary-work"));
            components = new DiskUnionFind(workspace.resolve("parents.bin"), dictionary.size());
            try (BufferedReader reader = Files.newBufferedReader(rawEdges, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int split = line.indexOf('\t');
                    if (split <= 0 || split == line.length() - 1) {
                        throw new ScanResultContractException("semantic table edge is malformed");
                    }
                    int left = dictionary.id(line.substring(0, split)).orElseThrow(
                            () -> new ScanResultContractException(
                                    "semantic table edge references an unknown table"));
                    int right = dictionary.id(line.substring(split + 1)).orElseThrow(
                            () -> new ScanResultContractException(
                                    "semantic table edge references an unknown table"));
                    components.union(left, right);
                }
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to build semantic table component index", failure);
        }
    }

    public String componentKey(String encodedTable) {
        if (encodedTable == null || encodedTable.isBlank()) {
            return "global";
        }
        try {
            int id = dictionary.id(encodedTable).orElseThrow(
                    () -> new ScanResultContractException("semantic owner table is absent from component index"));
            return "%010d".formatted(components.find(id));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to query semantic table component index", failure);
        }
    }

    @Override
    public void close() {
        IOException failure = null;
        try {
            components.close();
        } catch (IOException error) {
            failure = error;
        }
        try {
            dictionary.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("failed to close semantic table component index", failure);
        }
    }
}
