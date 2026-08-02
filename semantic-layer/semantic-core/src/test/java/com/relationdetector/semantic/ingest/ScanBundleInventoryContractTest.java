package com.relationdetector.semantic.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

final class ScanBundleInventoryContractTest {
    @Test
    void scanBundleRequiresAnExplicitMetadataInventory() {
        long publicConstructors = Arrays.stream(ScanBundle.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .count();

        assertEquals(1, publicConstructors);
        assertFalse(Arrays.stream(ScanMetadataInventory.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("emptyComplete")));
    }
}
