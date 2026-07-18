/**
 * CN: event 包负责从已验证的写入血缘和辅助关系中生成确定性事件候选。输入是 typed ScanBundle，输出交给 evidence graph 和 extraction bundle；上游是 reader，下游是 graph/extract。禁止从对象名或 SQL 文本猜测物理事实。
 * EN: The event package derives deterministic event candidates from validated write lineage and supporting relationships. It consumes typed ScanBundle data and emits candidates to graph and extraction downstream consumers; it must not infer physical facts from names or SQL text.
 */
package com.relationdetector.semantic.event;
