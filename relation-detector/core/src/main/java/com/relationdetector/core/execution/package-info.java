/**
 * CN: 责任是执行有界statement任务并保持串并行错误类别一致；输入为已验证statement和parser bundle，输出为
 * 候选或原样传播的typed失败。上游是scan编排，下游是relationship/lineage提取；禁止选择配置、修改grammar
 * 或吞掉adaptor contract异常。
 * EN: Responsibility: execute bounded statement work while preserving identical serial/parallel error categories.
 * Inputs are validated statements and parser bundles; outputs are candidates or propagated typed failures. Upstream is
 * scan orchestration and downstream is fact extraction. Configuration selection, grammar changes, and swallowed
 * adaptor contract failures are forbidden.
 */
package com.relationdetector.core.execution;
