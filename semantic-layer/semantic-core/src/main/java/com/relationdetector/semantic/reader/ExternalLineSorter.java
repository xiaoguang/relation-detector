package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * CN: 使用固定字节预算分块排序UTF-8单行记录，并以有界扇入多路归并产生稳定外排结果；输入是临时spool，
 * 输出是排序文件，本类不解析业务字段或长期保留索引。
 * EN: Sorts UTF-8 line records in fixed-size chunks and performs bounded fan-in merges into a stable external-sort
 * result. It owns temporary spools only and never interprets business fields or retains an index.
 */
final class ExternalLineSorter {
    private static final long DEFAULT_CHUNK_BYTES = 4L * 1024L * 1024L;
    private static final int MERGE_FAN_IN = 32;

    void sort(Path input, Path output, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        List<Path> chunks = createChunks(input, workspace);
        if (chunks.isEmpty()) {
            Files.writeString(output, "", StandardCharsets.UTF_8);
            return;
        }
        int pass = 0;
        while (chunks.size() > 1) {
            List<Path> merged = new ArrayList<>();
            for (int start = 0; start < chunks.size(); start += MERGE_FAN_IN) {
                List<Path> group = chunks.subList(start, Math.min(start + MERGE_FAN_IN, chunks.size()));
                Path target = workspace.resolve("merge-%03d-%06d.txt".formatted(pass, merged.size()));
                merge(group, target);
                merged.add(target);
            }
            delete(chunks);
            chunks = merged;
            pass++;
        }
        Files.move(chunks.get(0), output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private List<Path> createChunks(Path input, Path workspace) throws IOException {
        List<Path> chunks = new ArrayList<>();
        List<String> values = new ArrayList<>();
        long bytes = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                values.add(line);
                bytes += utf8Length(line) + 1L;
                if (bytes >= DEFAULT_CHUNK_BYTES) {
                    chunks.add(writeChunk(values, workspace, chunks.size()));
                    values.clear();
                    bytes = 0;
                }
            }
        }
        if (!values.isEmpty()) {
            chunks.add(writeChunk(values, workspace, chunks.size()));
        }
        return chunks;
    }

    private Path writeChunk(List<String> values, Path workspace, int index) throws IOException {
        values.sort(Comparator.naturalOrder());
        Path chunk = workspace.resolve("chunk-%06d.txt".formatted(index));
        try (BufferedWriter writer = Files.newBufferedWriter(
                chunk, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (String value : values) {
                writer.write(value);
                writer.newLine();
            }
        }
        return chunk;
    }

    private void merge(List<Path> inputs, Path output) throws IOException {
        List<BufferedReader> readers = new ArrayList<>();
        PriorityQueue<Head> pending = new PriorityQueue<>(
                Comparator.comparing(Head::value).thenComparingInt(Head::reader));
        try {
            for (int index = 0; index < inputs.size(); index++) {
                BufferedReader reader = Files.newBufferedReader(inputs.get(index), StandardCharsets.UTF_8);
                readers.add(reader);
                String value = reader.readLine();
                if (value != null) {
                    pending.add(new Head(value, index));
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                while (!pending.isEmpty()) {
                    Head head = pending.remove();
                    writer.write(head.value());
                    writer.newLine();
                    String next = readers.get(head.reader()).readLine();
                    if (next != null) {
                        pending.add(new Head(next, head.reader()));
                    }
                }
            }
        } finally {
            for (BufferedReader reader : readers) {
                reader.close();
            }
        }
    }

    private long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private void delete(List<Path> paths) throws IOException {
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private record Head(String value, int reader) {
    }
}
