/**
 * CN: 系统与客户 naming rule 执行及 top-level naming evidence。
 *
 * <p>EN: System and customer naming-rule execution and top-level naming evidence.
 * <p>Responsibility: 执行唯一 naming rule engine 并聚合 top-level naming evidence / Runs the sole naming-rule engine.
 * <p>Inputs: 有向 endpoint pairs、系统/用户规则与 structural observations / Directional pairs, rules, and observations.
 * <p>Outputs: 可被 relationship evidenceRef 引用的 naming facts / Top-level naming facts referenced by relationships.
 * <p>Upstream/Downstream: direct candidates 上游，merger/derived naming 下游 / Between candidates and naming consumers.
 * <p>Forbidden: naming 包外不得运行 suffix/plural/alias heuristics / Naming heuristics must not escape this package.
 */
package com.relationdetector.core.naming;
