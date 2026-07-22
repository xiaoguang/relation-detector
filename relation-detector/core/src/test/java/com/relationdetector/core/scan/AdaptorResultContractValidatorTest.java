package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;

class AdaptorResultContractValidatorTest {
    private final AdaptorResultContractValidator validator = new AdaptorResultContractValidator();

    @Test
    void metadataInventoryRequiresValidColumnOrdinal() {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.columnFacts().add(new MetadataColumnFact(
                null, "shop", "orders", "id", "BIGINT", "BIGINT", false,
                null, null, null, 0));

        assertThrows(AdaptorContractException.class, () -> validator.validateMetadata(snapshot));
    }

    @Test
    void metadataResultDetachesNestedPluginContainers() {
        List<String> mutablePath = new ArrayList<>(List.of("shop.orders.customer_id"));
        RelationshipCandidate candidate = metadataRelationship();
        candidate.attributes().put("path", mutablePath);
        candidate.evidence().clear();
        candidate.evidence().add(new Evidence(
                EvidenceType.METADATA_FOREIGN_KEY,
                java.math.BigDecimal.valueOf(0.98d),
                EvidenceSourceType.METADATA,
                "catalog",
                "fk",
                Map.of("path", mutablePath)));
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.relationships().add(candidate);

        MetadataSnapshot validated = validator.validateMetadata(snapshot);
        mutablePath.add("plugin-mutated");
        snapshot.relationships().clear();

        assertEquals(List.of("shop.orders.customer_id"),
                validated.relationships().get(0).attributes().get("path"));
        assertEquals(List.of("shop.orders.customer_id"),
                validated.relationships().get(0).evidence().get(0).attributes().get("path"));
        assertEquals(1, validated.relationships().size());
    }

    @Test
    void blankAndNullDefinitionsRemainRecoverable() {
        var objects = validator.validateObjects(Arrays.asList(
                null,
                new DatabaseObjectDefinition(
                        DatabaseObjectType.PROCEDURE, null, "shop", "rebuild", " ", "catalog")),
                List.of());
        var ddls = validator.validateDatabaseDdl(Arrays.asList(
                null,
                new DatabaseDdlDefinition(null, "shop", "orders", "", "catalog")),
                List.of());

        assertTrue(objects.definitions().isEmpty());
        assertEquals(List.of("DEFINITION_UNAVAILABLE", "DEFINITION_UNAVAILABLE"),
                objects.warnings().stream().map(WarningMessage::code).toList());
        assertTrue(ddls.definitions().isEmpty());
        assertEquals(List.of("DEFINITION_UNAVAILABLE", "DEFINITION_UNAVAILABLE"),
                ddls.warnings().stream().map(WarningMessage::code).toList());
    }

    @Test
    void nonBlankDefinitionsRequireCompleteIdentityAndSource() {
        assertThrows(AdaptorContractException.class, () -> validator.validateObjects(
                List.of(new DatabaseObjectDefinition(
                        null, null, "shop", "rebuild", "SELECT 1", "catalog")),
                List.of()));
        assertThrows(AdaptorContractException.class, () -> validator.validateObjects(
                List.of(new DatabaseObjectDefinition(
                        DatabaseObjectType.PROCEDURE, null, "shop", "", "SELECT 1", "catalog")),
                List.of()));
        assertThrows(AdaptorContractException.class, () -> validator.validateDatabaseDdl(
                List.of(new DatabaseDdlDefinition(null, "shop", "orders", "CREATE TABLE orders(id INT)", "")),
                List.of()));
    }

    @Test
    void liveCallbackWarningIsRebuiltWithoutPluginContent() {
        WarningMessage pluginWarning = WarningMessage.warn(
                WarningType.PERMISSION_WARNING,
                "OBJECT_COLLECT_FAILED",
                "SELECT password FROM secrets",
                "jdbc:secret://password=secret",
                41,
                Map.of(
                        "sqlState", "42501",
                        "vendorCode", 229,
                        "exceptionClass", "java.sql.SQLException",
                        "objectSchema", "shop",
                        "objectName", "rebuild",
                        "secret", "password=secret"));

        var result = validator.validateObjects(List.of(), List.of(pluginWarning));

        WarningMessage rebuilt = result.warnings().get(0);
        assertEquals("Live database object collection permission denied", rebuilt.message());
        assertEquals("database-objects", rebuilt.source());
        assertEquals(0L, rebuilt.line());
        assertEquals("shop", rebuilt.attributes().get("objectSchema"));
        assertFalse(rebuilt.attributes().containsKey("secret"));
        assertFalse(rebuilt.toString().contains("password=secret"));
    }

    @Test
    void invalidLiveCallbackEnvelopeRejectsTheWholeOutcome() {
        DatabaseObjectDefinition valid = new DatabaseObjectDefinition(
                DatabaseObjectType.PROCEDURE, null, "shop", "rebuild", "SELECT 1", "catalog");
        WarningMessage wrongType = WarningMessage.warn(
                WarningType.PARSE_WARNING, "PLUGIN_WARNING", "invalid", "plugin", 1);
        WarningMessage unsafeCode = WarningMessage.warn(
                WarningType.LIVE_SOURCE_WARNING, "plugin-warning", "invalid", "plugin", 1);

        assertThrows(AdaptorContractException.class,
                () -> validator.validateObjects(List.of(valid), List.of(wrongType)));
        assertThrows(AdaptorContractException.class,
                () -> validator.validateObjects(List.of(valid), List.of(unsafeCode)));
    }

    private RelationshipCandidate metadataRelationship() {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(ColumnRef.of(TableId.of("shop", "orders"), "customer_id")),
                Endpoint.column(ColumnRef.of(TableId.of("shop", "customers"), "id")),
                RelationType.FK_LIKE,
                RelationSubType.DECLARED_FK);
        candidate.evidence().add(Evidence.of(
                EvidenceType.METADATA_FOREIGN_KEY, 0.98d,
                EvidenceSourceType.METADATA, "catalog", "fk"));
        return candidate;
    }
}
