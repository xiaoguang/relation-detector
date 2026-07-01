package com.relationdetector.oracle.routine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OracleRoutineScopeTest {
    @Test
    void controlBlockEndDoesNotLeaveRoutineScope() {
        OracleRoutineScope scope = new OracleRoutineScope();

        scope.enterRoutine();
        scope.leaveRoutineEnd(true);

        assertTrue(scope.insideRoutine(), "END IF/END LOOP must not leave the surrounding routine");

        scope.leaveRoutineEnd(false);
        assertFalse(scope.insideRoutine(), "plain END leaves the routine");
    }
}
