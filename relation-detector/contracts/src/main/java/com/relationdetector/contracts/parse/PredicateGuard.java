package com.relationdetector.contracts.parse;

/**
 * CN: 保存结构谓词上的列到字面量守卫条件，供 conditional relationship 汇总使用。
 * EN: Stores a typed column-to-literal guard on a structural predicate for conditional relationship summaries.
 */
public record PredicateGuard(
        ExpressionSource discriminator,
        String operator,
        String literalValue
) {
    public PredicateGuard {
        discriminator = discriminator == null ? ExpressionSource.EMPTY : discriminator;
        operator = operator == null ? "" : operator;
        literalValue = literalValue == null ? "" : literalValue;
    }
}
