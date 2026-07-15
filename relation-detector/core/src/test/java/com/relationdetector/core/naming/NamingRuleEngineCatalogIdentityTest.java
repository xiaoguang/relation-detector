package com.relationdetector.core.naming;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;

class NamingRuleEngineCatalogIdentityTest {

    @Test
    void doesNotTreatSameSchemaAndTableAcrossCatalogsAsSelfRole() {
        TableId sourceTable = new TableId("catalog_a", "hr", "employees", "hr.employees");
        TableId targetTable = new TableId("catalog_b", "hr", "employees", "hr.employees");
        Endpoint source = Endpoint.column(ColumnRef.of(sourceTable, "manager_id"));
        Endpoint target = Endpoint.column(ColumnRef.of(targetTable, "id"));

        var matches = new NamingRuleEngine().match(
                source,
                target,
                NamingRuleScope.RELATIONSHIP_CANDIDATE,
                true,
                NamingRuleSet.systemDefault());

        assertTrue(matches.stream().noneMatch(match -> match.rule().equals("SELF_ROLE_ID")),
                "Same table names in different catalogs are not a self-reference");
    }
}
