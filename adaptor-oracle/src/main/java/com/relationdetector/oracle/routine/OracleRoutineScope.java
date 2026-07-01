package com.relationdetector.oracle.routine;

/**
 * Oracle routine-scope state shared by token-event and full-grammer visitors.
 *
 * <p>Oracle PL/SQL bodies are parsed by the outer Oracle grammar, unlike
 * PostgreSQL dollar-quoted bodies that need a second body parser. This class
 * keeps the routine boundary in the dialect-level package so parser modes do
 * not duplicate routine ownership rules.
 */
public final class OracleRoutineScope {
    private int depth;

    public void enterRoutine() {
        depth++;
    }

    public void leaveRoutine() {
        if (depth > 0) {
            depth--;
        }
    }

    public void leaveRoutineEnd(boolean controlBlockEnd) {
        if (!controlBlockEnd) {
            leaveRoutine();
        }
    }

    public boolean insideRoutine() {
        return depth > 0;
    }
}
