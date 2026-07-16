/**
 * CN: MySQL 方言级 routine 解析与作用域策略。
 *
 * <p>EN: The package is the stable home for routine helpers shared by MySQL
 * token-event and MySQL 8.0 full-grammar paths. It is intentionally outside
 * parser-mode-specific packages.
 * <p>Responsibility: 管理 MySQL routine parameters、locals 与 trigger pseudo-rowsets / Manages routine symbol scope.
 * <p>Inputs: typed declaration contexts and nested block events / Typed declarations and nested blocks.
 * <p>Outputs: non-physical identifier decisions for expression analysis / Symbol decisions used by expression analysis.
 * <p>Upstream/Downstream: MySQL visitors 上游，column-read extraction 下游 / Between visitors and column extraction.
 * <p>Forbidden: 不按 p_/v_ 前缀或 raw text 猜变量 / Must not infer variables from prefixes or raw text.
 */
package com.relationdetector.mysql.routine;
