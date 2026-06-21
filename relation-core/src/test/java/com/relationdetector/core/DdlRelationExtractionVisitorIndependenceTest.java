package com.relationdetector.core;

import com.relationdetector.core.ddl.*;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.parser.*;
import com.relationdetector.core.relation.*;

import com.relationdetector.core.tokenevent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Proves the token-event DDL relation extractor consumes structured DDL events.
 *
 * <p>The raw input intentionally contains no DDL relationship, proving the
 * visitor relies on structured events instead of reparsing raw DDL text.
 */
class DdlRelationExtractionVisitorIndependenceTest {
    @Test
    void extractsForeignKeyFromStructuredDdlEventsWhenRawDdlHasNoRelation() {
        StructuredParseResult structured = structured(List.of(
                fk("orders", "user_id", "users", "id", 1, 1),
                index("orders", "user_id", "SOURCE_INDEX"),
                index("users", "id", "TARGET_UNIQUE")
        ));

        List<RelationshipCandidate> relations = new DdlRelationExtractionVisitor()
                .extract("CREATE TABLE unrelated(id BIGINT)", "ddl-events.sql", structured);

        assertEquals(1, relations.size());
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.FK_LIKE, relation.relationType());
        assertEquals("orders.user_id", relation.source().displayName());
        assertEquals("users.id", relation.target().displayName());
        assertTrue(relation.evidence().stream().anyMatch(evidence -> evidence.type().name().equals("DDL_FOREIGN_KEY")));
        assertTrue(relation.evidence().stream().anyMatch(evidence -> evidence.type().name().equals("SOURCE_INDEX")));
        assertTrue(relation.evidence().stream().anyMatch(evidence -> evidence.type().name().equals("TARGET_UNIQUE")));
    }

    @Test
    void doesNotFallBackToRawDdlWhenStructuredEventsAreEmpty() {
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new DdlRelationExtractionVisitor()
                .extract("""
                        CREATE TABLE users(id BIGINT PRIMARY KEY);
                        CREATE TABLE orders(user_id BIGINT REFERENCES users(id));
                        """, "raw-has-fk.sql", structured);

        assertTrue(relations.isEmpty(), () -> "Empty token-event DDL events must not emit relationships: " + relations);
    }

    private StructuredParseResult structured(List<StructuredSqlEvent> events) {
        return new StructuredParseResult("ANTLR", "MYSQL", "ddl-events.sql", events, List.of(), Map.of());
    }

    private StructuredSqlEvent fk(String sourceTable, String sourceColumn, String targetTable, String targetColumn,
                                  int position, int size) {
        return new StructuredSqlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, "ddl-events.sql", 1,
                Map.of("sourceTable", sourceTable, "sourceColumn", sourceColumn,
                        "targetTable", targetTable, "targetColumn", targetColumn,
                        "compositePosition", position, "compositeSize", size));
    }

    private StructuredSqlEvent index(String table, String column, String role) {
        return new StructuredSqlEvent(StructuredParseEventType.DDL_INDEX, "ddl-events.sql", 1,
                Map.of("table", table, "column", column, "role", role));
    }
}
