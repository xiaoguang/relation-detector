package com.relationdetector.contracts.model;

import java.util.List;

/**
 * CN: 这是从 catalog、DDL inventory 或 typed SQL predicate 提取的命名证据，不是 relationship fact。它可以被输出给审计/语义层，也可以在
 * 已有 SQL/DDL/metadata relationship candidate 上作为附加 evidence 参与定向和加分，
 * 但不能单独生成最终 relationship。
 * EN: Represents name-only evidence from catalog, DDL, or typed SQL predicates; it may enrich an existing candidate but cannot create a relationship by itself.
 */
public record NamingEvidenceCandidate(
        Endpoint source,
        Endpoint target,
        Evidence evidence,
        String rule,
        boolean directionHint,
        List<Evidence> rawEvidence
) {
    public NamingEvidenceCandidate(
            Endpoint source,
            Endpoint target,
            Evidence evidence,
            String rule,
            boolean directionHint
    ) {
        this(source, target, evidence, rule, directionHint, List.of(evidence));
    }

    public NamingEvidenceCandidate {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        if (evidence == null) {
            throw new IllegalArgumentException("evidence is required");
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("rule is required");
        }
        if (rawEvidence == null || rawEvidence.isEmpty()) {
            rawEvidence = List.of(evidence);
        } else {
            rawEvidence = List.copyOf(rawEvidence);
        }
    }

    public String id() {
        return "naming:" + source.normalizedKey() + "->" + target.normalizedKey() + ":" + rule;
    }
}
