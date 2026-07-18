package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;

/**
 * CN: 将方言客户端脚本 framing 与 server SQL grammar parsing 分离，只输出 statement 边界与 warnings。
 * EN: Separates dialect client-script framing from server SQL grammar parsing and emits only statement boundaries and warnings.
 */
@FunctionalInterface
public interface DialectScriptFramer {
    ScriptFrameResult frame(ScriptFrameRequest request);
}
