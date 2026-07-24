package com.relationdetector.semantic.extract;

/**
 * CN: 表示 evidence-closed 切片无法满足预算、覆盖或唯一归属契约；输入来自 planner/validator，输出为原子失败，禁止退化为静默截断。
 * EN: Reports an evidence-closed sharding failure involving budget, coverage, or unique ownership. It is an atomic failure and must never become silent truncation.
 */
public final class SemanticShardingException extends IllegalArgumentException {
    public SemanticShardingException(String message) {
        super(message);
    }
}
