package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;

/**
 *
 * Separates dialect client-script framing from server SQL grammar parsing.
 */
@FunctionalInterface
public interface DialectScriptFramer {
    ScriptFrameResult frame(ScriptFrameRequest request);
}
