/**
 * Oracle dialect-level routine parsing and scope policy.
 *
 * <p>Oracle token-event and versioned full-grammar parsers own their generated
 * grammar packages separately, but routine body handling belongs to the dialect
 * layer so it can be shared without delegating between parser modes.
 */
package com.relationdetector.oracle.routine;
