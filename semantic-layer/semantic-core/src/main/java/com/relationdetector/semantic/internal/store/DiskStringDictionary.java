package com.relationdetector.semantic.internal.store;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * CN: 将外排去重后的字符串key映射为稳定dense integer id，lookup使用磁盘二分且不会把dictionary加载进堆；
 * 供file-backed union-find使用，不解释key内容。
 * EN: Maps externally sorted unique string keys to stable dense integer ids. Lookup uses on-disk binary search and
 * never loads the dictionary into heap; the dictionary does not interpret key content.
 */
public final class DiskStringDictionary implements AutoCloseable {
    private final Path path;
    private final DiskSortedTextFile file;
    private final int size;

    private DiskStringDictionary(Path path, int size) throws IOException {
        this.path = path;
        this.file = new DiskSortedTextFile(path);
        this.size = size;
    }

    public static DiskStringDictionary build(Path rawKeys, Path dictionary, Path workspace) throws IOException {
        Path sorted = workspace.resolve("keys.sorted");
        new ExternalLineSorter().sort(rawKeys, sorted, workspace.resolve("sort"));
        String previous = null;
        int id = 0;
        try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                     dictionary, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String key;
            while ((key = reader.readLine()) != null) {
                if (key.equals(previous)) {
                    continue;
                }
                writer.write(key);
                writer.write('\t');
                writer.write("%010d".formatted(id++));
                writer.newLine();
                previous = key;
            }
        } finally {
            Files.deleteIfExists(sorted);
        }
        return new DiskStringDictionary(dictionary, id);
    }

    public int size() {
        return size;
    }

    public OptionalInt id(String key) throws IOException {
        return file.findTabKey(key)
                .map(line -> OptionalInt.of(Integer.parseInt(line.substring(line.lastIndexOf('\t') + 1))))
                .orElseGet(OptionalInt::empty);
    }

    public void forEach(Consumer<Entry> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.lastIndexOf('\t');
                consumer.accept(new Entry(line.substring(0, split), Integer.parseInt(line.substring(split + 1))));
            }
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    public record Entry(String key, int id) {
    }

}
