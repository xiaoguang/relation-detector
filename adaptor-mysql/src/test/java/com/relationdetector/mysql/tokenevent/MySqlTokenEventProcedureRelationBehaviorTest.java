package com.relationdetector.mysql.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class MySqlTokenEventProcedureRelationBehaviorTest {
    @Test
    void extractsProcedureJoinRelationsFromBasicCorrectnessFixture() throws Exception {
        String sql = objectBlock("PROCEDURE:case_01.proc_generate_purchase_inbound_from_order");
        SqlStatementRecord statement = new SqlStatementRecord(sql, StatementSourceType.PROCEDURE,
                "PROCEDURE:case_01.proc_generate_purchase_inbound_from_order", 1, 1, Map.of());

        var structured = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);
        assertFalse(structured.events().isEmpty(), structured.attributes().toString());
        List<String> fingerprints = new TokenEventRelationExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::fingerprint)
                .sorted()
                .toList();

        assertEquals(List.of(
                "CO_OCCURRENCE:jsh_depot_item.material_id->jsh_material_extend.material_id:SQL_LOG_COLUMN_CO_OCCURRENCE",
                "CO_OCCURRENCE:jsh_material_current_stock.depot_id->jsh_depot_item.depot_id:SQL_LOG_COLUMN_CO_OCCURRENCE",
                "CO_OCCURRENCE:jsh_material_current_stock.material_id->jsh_depot_item.material_id:SQL_LOG_COLUMN_CO_OCCURRENCE",
                "FK_LIKE:jsh_depot_item.header_id->jsh_depot_head.id:PROCEDURE_JOIN",
                "FK_LIKE:jsh_depot_item.material_id->jsh_material.id:PROCEDURE_JOIN"), fingerprints,
                () -> structured.events().stream()
                        .filter(event -> switch (event.type()) {
                            case ROWSET_REFERENCE, PREDICATE_EQUALITY, PROJECTION_ITEM, IGNORED_ROWSET -> true;
                            default -> false;
                        })
                        .map(event -> event.type() + ":" + event.attributes())
                        .toList()
                        .toString());
    }

    private String objectBlock(String marker) throws Exception {
        Path input = workspaceRoot().resolve("test-fixtures/mysql/basic-correctness/case-01/sql/routines-procedures.sql");
        List<String> lines = Files.readAllLines(input);
        List<String> block = new ArrayList<>();
        boolean inBlock = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("-- relation-detector-fixture-source: " + marker)) {
                inBlock = true;
                continue;
            }
            if (inBlock && trimmed.equals("-- relation-detector-fixture-end")) {
                return String.join("\n", block);
            }
            if (inBlock) {
                block.add(line);
            }
        }
        throw new IllegalArgumentException("Cannot find fixture source marker " + marker);
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("test-fixtures"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate relation-detector workspace root");
    }

    private String fingerprint(RelationshipCandidate relation) {
        String evidenceTypes = relation.evidence().stream()
                .map(evidence -> evidence.type().name())
                .collect(java.util.stream.Collectors.joining(","));
        return relation.relationType() + ":"
                + relation.source().displayName() + "->" + relation.target().displayName()
                + ":" + evidenceTypes;
    }
}
