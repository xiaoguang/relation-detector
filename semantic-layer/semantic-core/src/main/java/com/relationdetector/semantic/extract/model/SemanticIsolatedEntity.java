package com.relationdetector.semantic.extract.model;

/**
 * CN: 记录未与任何可解析语义边连接的 entity 及其原因，供 validation/review 展示；它不删除实体。
 * EN: Records a semantic entity that has no resolvable semantic edge plus the reason for validation and review. It does not remove the entity.
 */
public record SemanticIsolatedEntity(String id, String name, String physicalName, String reason) {
}
