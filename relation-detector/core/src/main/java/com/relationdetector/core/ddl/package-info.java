/**
 * token-event DDL 事件来源层。
 *
 * <p>CN: 本包解析 CREATE/ALTER/INDEX DDL 文本并输出 DDL_FOREIGN_KEY /
 * DDL_INDEX 结构事件。它不直接创建 RelationshipCandidate；DDL relationship
 * 转换集中在 core.relation.DdlRelationExtractionVisitor。
 *
 * <p>EN: Token-event DDL event-source layer. It parses CREATE/ALTER/INDEX DDL
 * text into DDL_FOREIGN_KEY and DDL_INDEX structured events. It does not create
 * RelationshipCandidate instances directly; DDL relationship conversion lives
 * in core.relation.DdlRelationExtractionVisitor.
 * <p>Responsibility: 汇总 typed DDL inventory 与约束证据 / Aggregates typed DDL inventory and constraint evidence.
 * <p>Inputs: adaptor structured DDL events / Structured DDL events from adaptors.
 * <p>Outputs: relationship enhancement 可消费的 index/unique/column observations / DDL observations for enhancement.
 * <p>Upstream/Downstream: parser 上游，relation/metadata enhancement 下游 / Between parsers and enhancement layers.
 * <p>Forbidden: index 本身不得创建 relationship / An index alone must not create a relationship.
 */
package com.relationdetector.core.ddl;
