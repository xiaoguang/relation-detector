/**
 * CN: Oracle 方言级 routine 解析与作用域策略。
 *
 * <p>EN: Oracle token-event and versioned full-grammar parsers own their generated
 * grammar packages separately, but routine body handling belongs to the dialect
 * layer so it can be shared without delegating between parser modes.
 * <p>Responsibility: 跟踪 Oracle routine parameters、locals 与 nested scopes / Tracks Oracle routine symbols.
 * <p>Inputs: typed parameter/variable declarations and scope boundaries / Typed declarations and boundaries.
 * <p>Outputs: expression analyzer 使用的 non-physical symbol decisions / Symbol decisions for expression analysis.
 * <p>Upstream/Downstream: Oracle visitors 上游，column-read extraction 下游 / Between visitors and column extraction.
 * <p>Forbidden: 不按 p_/v_ 名称前缀排除 endpoint / Must not classify symbols by naming prefixes.
 */
package com.relationdetector.oracle.routine;
