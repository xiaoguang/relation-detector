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
 */
package com.relationdetector.core.ddl;
