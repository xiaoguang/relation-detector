package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.relationdetector.core.log.SourceNameNormalizer;

class SourceNameNormalizerTest {
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
}
