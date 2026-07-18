package com.relationdetector.core.lineage.model;

import java.util.Set;

import com.relationdetector.contracts.model.TableId;

/**
 * CN: 承载 routine parameter、local variable 与 local table symbols，这些 symbols 不得成为物理 lineage endpoints。
 * EN: Carries routine parameters, local variables, and local-table symbols that must not become physical lineage endpoints.
 */
public record RoutineScope(
        Set<String> parameters,
        Set<String> localVariables,
        Set<TableId> localTables
) {
}
