/**
 * CN: 责任是承载跨模块共享的typed identifier结构原语；输入为parser已确认的标识符文本，输出为保留引号
 * 边界的分段结果。上游是adaptor/core typed parser，下游是identity与semantic endpoint；禁止扫描SQL、补全
 * namespace或执行名称启发式。
 * EN: Responsibility: shared typed-identifier structure primitives. Inputs are parser-confirmed identifier texts;
 * outputs preserve quoted segment boundaries. Upstream is adaptor/core parsing and downstream is identity/semantic
 * endpoint handling. SQL scanning, namespace completion, and naming heuristics are forbidden.
 */
package com.relationdetector.contracts.identifier;
