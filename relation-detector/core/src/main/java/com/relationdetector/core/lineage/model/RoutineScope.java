package com.relationdetector.core.lineage.model;

import java.util.Set;

import com.relationdetector.contracts.model.TableId;

/**
 *
 * Routine-local scope facts that should not become physical lineage endpoints.
 */
public record RoutineScope(
        Set<String> parameters,
        Set<String> localVariables,
        Set<TableId> localTables
) {
}
