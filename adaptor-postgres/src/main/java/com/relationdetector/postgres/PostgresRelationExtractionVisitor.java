package com.relationdetector.postgres;

import com.relationdetector.core.RelationExtractionVisitor;

/**
 * PostgreSQL relation extraction visitor for the ANTLR SQL path.
 *
 * <p>The shared {@link RelationExtractionVisitor} now independently converts
 * structured ANTLR events into relationship candidates. This subclass keeps the
 * PostgreSQL extension point explicit: future PostgreSQL-only rules such as
 * {@code LATERAL}, set-returning functions, {@code ONLY}, or advanced
 * {@code MERGE} shapes can move here without changing MySQL behavior.
 */
public final class PostgresRelationExtractionVisitor extends RelationExtractionVisitor {
}
