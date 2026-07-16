package com.relationdetector.contracts.parse;

/**
 *
 * Typed column-to-literal condition guarding a structural predicate.
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
