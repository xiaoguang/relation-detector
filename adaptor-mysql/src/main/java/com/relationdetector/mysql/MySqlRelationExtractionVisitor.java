package com.relationdetector.mysql;

import com.relationdetector.core.RelationExtractionVisitor;

/**
 * MySQL relation extraction visitor for the ANTLR SQL path.
 *
 * <p>The shared {@link RelationExtractionVisitor} now independently converts
 * structured ANTLR events into relationship candidates. This subclass keeps the
 * MySQL extension point explicit: future MySQL-only rules such as
 * {@code JSON_TABLE}, optimizer hints, or dialect-specific multi-table DML can
 * move here without changing PostgreSQL behavior.
 */
public final class MySqlRelationExtractionVisitor extends RelationExtractionVisitor {
}
