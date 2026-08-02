package com.relationdetector.semantic.internal.store;

import com.relationdetector.semantic.ingest.ScanResultContractException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * CN: 将外排后的UTF-8 key文件作为磁盘membership index，构建时检测重复，查询时按文件偏移二分且不加载完整
 * key集合；上游是streaming reader/evidence store，下游是identity与reference closure校验。
 * EN: Uses an externally sorted UTF-8 key file as an on-disk membership index. Construction rejects duplicates and
 * lookup uses file-offset binary search without loading the complete key set.
 */
public final class SortedTextIndex implements AutoCloseable {
    private final Path path;
    private final DiskSortedTextFile file;

    private SortedTextIndex(Path path) throws IOException {
        this.path = path;
        this.file = new DiskSortedTextFile(path);
    }

    public static SortedTextIndex build(Path rawKeys, Path index, Path workspace, String label) throws IOException {
        Path sorted = workspace.resolve(index.getFileName() + ".sorted");
        new ExternalLineSorter().sort(rawKeys, sorted, workspace.resolve(index.getFileName() + ".sort-work"));
        String previous = null;
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             var writer = Files.newBufferedWriter(
                     index, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String current;
            while ((current = reader.readLine()) != null) {
                if (current.equals(previous)) {
                    throw new ScanResultContractException(label + " contains duplicate identity");
                }
                writer.write(current);
                writer.newLine();
                previous = current;
            }
        } finally {
            Files.deleteIfExists(sorted);
        }
        return new SortedTextIndex(index);
    }

    public static SortedTextIndex openExisting(Path index) throws IOException {
        if (index == null || !Files.isRegularFile(index)) {
            throw new IllegalArgumentException("sorted text index file is required");
        }
        return new SortedTextIndex(index);
    }

    public boolean contains(String key) throws IOException {
        if (key == null || key.isBlank()) {
            return false;
        }
        return file.find(key).isPresent();
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

}
