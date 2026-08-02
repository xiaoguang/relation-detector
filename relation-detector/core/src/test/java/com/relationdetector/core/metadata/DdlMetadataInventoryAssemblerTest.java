package com.relationdetector.core.metadata;

import com.relationdetector.core.result.MetadataInventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.parse.DdlCatalogEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.ddl.DdlCatalogInventory;
import com.relationdetector.core.identity.NamespaceContext;

final class DdlMetadataInventoryAssemblerTest {
    private static final IdentifierRules IDENTIFIERS = String::toLowerCase;
    private static final ScanScope SCOPE = new ScanScope("", "app", List.of(), List.of());

    @Test
    void completeTypedDdlCreatesCompleteDeclarationInventory() {
        MetadataInventory inventory = new DdlMetadataInventoryAssembler().assemble(
                MetadataInventory.empty(MetadataInventoryStatus.NOT_REQUESTED, MetadataInventoryBasis.NONE, SCOPE),
                ddlInventory(List.of()),
                SCOPE);

        assertEquals(MetadataInventoryStatus.COMPLETE, inventory.status());
        assertEquals(MetadataInventoryBasis.DDL_DECLARATIONS, inventory.basis());
        assertEquals("app", inventory.tables().get(0).schema());
        assertEquals("orders", inventory.columns().get(0).tableName());
    }

    @Test
    void liveAndTypedDdlCreateMergedInventoryWithLiveFactsPreferred() {
        MetadataSnapshot live = new MetadataSnapshot();
        live.tableFacts().add(new MetadataTableFact(null, "app", "orders", "TABLE", "heap", "live"));
        live.columnFacts().add(new MetadataColumnFact(
                null, "app", "orders", "id", "BIGINT", "BIGINT", false,
                null, "", "", 1));
        MetadataInventory inventory = new DdlMetadataInventoryAssembler().assemble(
                MetadataInventory.from(
                        MetadataInventoryStatus.COMPLETE,
                        MetadataInventoryBasis.LIVE_METADATA,
                        SCOPE,
                        live),
                ddlInventory(List.of()),
                SCOPE);

        assertEquals(MetadataInventoryStatus.COMPLETE, inventory.status());
        assertEquals(MetadataInventoryBasis.MERGED, inventory.basis());
        assertEquals("BIGINT", inventory.columns().get(0).dataType());
    }

    @Test
    void typedCoverageGapProducesPartialDeclarationInventory() {
        MetadataInventory inventory = new DdlMetadataInventoryAssembler().assemble(
                MetadataInventory.empty(MetadataInventoryStatus.NOT_REQUESTED, MetadataInventoryBasis.NONE, SCOPE),
                ddlInventory(List.of("UNSUPPORTED_TYPED_DECLARATION")),
                SCOPE);

        assertEquals(MetadataInventoryStatus.PARTIAL, inventory.status());
        assertEquals(MetadataInventoryBasis.DDL_DECLARATIONS, inventory.basis());
    }

    @Test
    void unresolvedIndexReferenceProducesPartialDeclarationInventory() {
        DdlCatalogEvent event = new DdlCatalogEvent(
                StructuredParseEventType.DDL_CATALOG,
                SourceProvenance.source("schema.sql", 1),
                List.of(),
                List.of(),
                List.of(),
                List.of(new MetadataIndexFact(
                        null, null, "orders", "idx_orders_customer",
                        false, false, "INDEX", true,
                        List.of(MetadataIndexMemberFact.fullColumn(1, "customer_id")))),
                List.of());
        DdlCatalogInventory ddl = new DdlCatalogInventory();
        ddl.add(event, IDENTIFIERS, new NamespaceContext("", "app", List.of()));

        MetadataInventory inventory = new DdlMetadataInventoryAssembler().assemble(
                MetadataInventory.empty(MetadataInventoryStatus.NOT_REQUESTED, MetadataInventoryBasis.NONE, SCOPE),
                ddl,
                SCOPE);

        assertEquals(MetadataInventoryStatus.PARTIAL, inventory.status());
    }

    private DdlCatalogInventory ddlInventory(List<String> gaps) {
        DdlCatalogEvent event = new DdlCatalogEvent(
                StructuredParseEventType.DDL_CATALOG,
                SourceProvenance.source("schema.sql", 1),
                List.of(new MetadataTableFact(null, null, "orders", "TABLE", null, null)),
                List.of(new MetadataColumnFact(
                        null, null, "orders", "id", "UNKNOWN", "UNKNOWN", true,
                        null, "", "", 1)),
                List.of(),
                List.of(),
                gaps);
        DdlCatalogInventory inventory = new DdlCatalogInventory();
        inventory.add(event, IDENTIFIERS, new NamespaceContext("", "app", List.of()));
        return inventory;
    }
}
