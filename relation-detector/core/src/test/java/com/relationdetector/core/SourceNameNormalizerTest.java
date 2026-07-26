package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.core.log.SourceNameNormalizer;

class SourceNameNormalizerTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesWorkspaceFileSourceToRelativePath() {
        Path absolute = Path.of("").toAbsolutePath()
                .resolve("relation-detector/sample-data/sqlserver/2025/03-data/07-erp-deep-scenario-data.sql")
                .normalize();

        assertEquals("relation-detector/sample-data/sqlserver/2025/03-data/07-erp-deep-scenario-data.sql",
                SourceNameNormalizer.normalize(absolute));
    }

    @Test
    void keepsObjectAndDerivedSourcesAsLabels() {
        assertEquals("ROUTINE:dbo.sp_run_mrp_for_plan",
                SourceNameNormalizer.normalize("ROUTINE:dbo.sp_run_mrp_for_plan"));
        assertEquals("TRIGGER:dbo.tr_orders_audit",
                SourceNameNormalizer.normalize("TRIGGER:dbo.tr_orders_audit"));
        assertEquals("derived:lineage",
                SourceNameNormalizer.normalize("derived:lineage"));
    }

    @Test
    void externalFileSourceUsesContentAddressWithoutLeakingAbsoluteDirectory() throws Exception {
        Path external = Files.writeString(tempDir.resolve("input.sql"), "SELECT 1;");

        String source = SourceNameNormalizer.normalizeFile(external, "SELECT 1;");

        assertEquals(
                "external/sha256-17db4fd369edb9244b9f91d9aeed145c3d04ad8ba6e95d06247f07a63527d11a/input.sql",
                source);
        assertEquals(source, SourceNameNormalizer.normalize(external));
        assertFalse(source.contains(tempDir.toString()));
    }
}
