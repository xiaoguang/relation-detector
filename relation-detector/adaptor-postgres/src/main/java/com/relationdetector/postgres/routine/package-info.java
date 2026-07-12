/**
 * PostgreSQL dialect-level routine body parsing.
 *
 * <p>This package is intentionally outside {@code fullgrammar}: token-event and
 * versioned full-grammar parsers both use it after extracting a PL/pgSQL routine
 * body from the outer statement.
 */
package com.relationdetector.postgres.routine;
