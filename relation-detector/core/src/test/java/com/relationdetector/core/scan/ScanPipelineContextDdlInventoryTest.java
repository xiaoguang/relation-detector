package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.ddl.DdlEvidenceInventory;
import com.relationdetector.core.identity.CanonicalEndpointKey;

class ScanPipelineContextDdlInventoryTest {
    @Test
    void aggregateInventoryUsesAdaptorRulesAndExplicitScanNamespace() {
        ScanConfig config = new ScanConfig();
        config.databaseType = DatabaseType.MYSQL;
        config.jdbcUrl = "jdbc:test:ddl-inventory";
        config.schema = "Shop";
        ScanScope scope = new ScanScope(null, "Shop", List.of(), List.of());
        DatabaseAdaptor adaptor = new CaseSensitiveAdaptor();
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of(null, "Orders"), "CustomerId")),
                Endpoint.column(ColumnRef.of(TableId.of(null, "Customers"), "Id")),
                RelationType.FK_LIKE, RelationSubType.INFERRED_JOIN_FK);

        try (ScanPipelineContext context = new ScanPipelineContext(
                ResolvedScanConfig.from(config), adaptor, scope,
                new ScanResult("MYSQL", "Shop"),
                new AdaptorContext(scope, Map.of(), warning -> { }),
                new ArrayList<>(), new ArrayList<>())) {
            context.ddlEvidenceInventory.addSourceIndex(
                    new CanonicalEndpointKey("", "Shop", "Orders", "CustomerId"),
                    new DdlEvidenceInventory.Observation(
                            "SOURCE_INDEX", "INDEX", EvidenceSourceType.DDL_FILE,
                            "schema.sql", 1, Map.of()));

            context.ddlEvidenceInventory.enhance(List.of(candidate));
        }

        assertTrue(candidate.evidence().stream().anyMatch(evidence ->
                evidence.type() == EvidenceType.SOURCE_INDEX));
    }

    private static final class CaseSensitiveAdaptor implements DatabaseAdaptor {
        @Override public int spiVersion() { return com.relationdetector.contracts.spi.AdaptorApiVersion.CURRENT; }
        @Override public String id() { return "case-sensitive"; }
        @Override public String displayName() { return "Case Sensitive"; }
        @Override public Set<DatabaseType> supportedDatabaseTypes() { return Set.of(DatabaseType.MYSQL); }
        @Override public Set<AdaptorCapability> capabilities() { return Set.of(); }
        @Override public IdentifierRules identifierRules() { return identifier -> identifier; }
        @Override public com.relationdetector.contracts.spi.AdaptorCollectors collectors() {
            return new com.relationdetector.contracts.spi.AdaptorCollectors(
                    Optional.of((connection, scope) -> new MetadataSnapshot()),
                    Optional.of((connection, scope) -> List.of()), Optional.empty(),
                    Optional.of((file, hint) -> Stream.empty()));
        }
        @Override public com.relationdetector.contracts.spi.AdaptorParsers parsers() {
            return new com.relationdetector.contracts.spi.AdaptorParsers(
                    (statement, context) -> List.of(), Optional.empty(), Optional.empty(),
                    request -> com.relationdetector.contracts.parse.ScriptFrameResult.empty());
        }
        @Override public com.relationdetector.contracts.spi.AdaptorProfiling profiling() {
            return new com.relationdetector.contracts.spi.AdaptorProfiling(
                    Optional.empty(), (evidence, context) -> evidence);
        }
    }
}
