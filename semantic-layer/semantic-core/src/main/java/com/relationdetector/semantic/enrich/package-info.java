/**
 * CN: enrichment 包负责对已构建的 evidence graph 应用可审计的语义补充。输入是完整证据图，输出仍保留原事实和 evidenceRefs 并交给 KG builder；上游是 graph builder，下游是 KG。禁止创造数据库关系、血缘或命名证据。
 * EN: The enrichment package applies auditable semantic additions to a completed evidence graph. Its input and output preserve physical facts and evidence references for the downstream KG builder; it must not create database relationships, lineage, or naming evidence.
 */
package com.relationdetector.semantic.enrich;
