package com.relationdetector.semantic.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Utf8DiskOrderingTest {
    @TempDir
    Path tempDir;

    @Test
    void externalSortAndWholeLineLookupUseTheSameUtf8Order() throws Exception {
        List<String> keys = List.of("\uE000", "ascii", "\uD83D\uDE00", "e\u0301");
        Path raw = tempDir.resolve("raw-keys.txt");
        Files.write(raw, keys);

        try (SortedTextIndex index = SortedTextIndex.build(
                raw, tempDir.resolve("index.txt"), tempDir.resolve("index-work"), "test keys")) {
            for (String key : keys) {
                assertTrue(index.contains(key), () -> "missing UTF-8 key " + key);
            }
        }
    }

    @Test
    void dictionaryTabLookupAndDenseIdsUseTheSameUtf8Order() throws Exception {
        List<String> keys = List.of("\uE000", "ascii", "\uD83D\uDE00", "e\u0301");
        Path raw = tempDir.resolve("dictionary-keys.txt");
        Files.write(raw, keys);

        List<DiskStringDictionary.Entry> entries = new ArrayList<>();
        try (DiskStringDictionary dictionary = DiskStringDictionary.build(
                raw, tempDir.resolve("dictionary.txt"), tempDir.resolve("dictionary-work"))) {
            for (String key : keys) {
                assertTrue(dictionary.id(key).isPresent(), () -> "missing dictionary key " + key);
            }
            dictionary.forEach(entries::add);
        }

        assertEquals(List.of("ascii", "e\u0301", "\uE000", "\uD83D\uDE00"),
                entries.stream().map(DiskStringDictionary.Entry::key).toList());
        assertEquals(List.of(0, 1, 2, 3),
                entries.stream().map(DiskStringDictionary.Entry::id).toList());
    }
}
