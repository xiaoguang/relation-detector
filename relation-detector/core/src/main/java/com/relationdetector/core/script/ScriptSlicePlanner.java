package com.relationdetector.core.script;

import java.util.List;

/**
 *
 * Plans statement character ranges for one client-script dialect.
 */
interface ScriptSlicePlanner {
    List<ScriptFramingSupport.Slice> plan(
            String text,
            List<ScriptLexeme> lexemes,
            List<ScriptFramingSupport.Slice> markedSlices
    );
}
