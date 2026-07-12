package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.parse.ScriptParseRequest;
import com.relationdetector.contracts.parse.ScriptParseResult;

/** Separates dialect client-script framing from server SQL grammar parsing. */
@FunctionalInterface
public interface DialectScriptParser {
    ScriptParseResult parse(ScriptParseRequest request);
}
