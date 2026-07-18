package com.relationdetector.semantic.extract.model;

/**
 * CN: 表示由 candidate/evidence 支撑的 subject-predicate-object 语义 triplet，并保留解析后的 owner refs；graph assembler 只消费已验证实例。
 * EN: Represents an evidence-backed subject-predicate-object semantic triplet with resolved owner references. Graph assembly consumes only normalized instances.
 */
public final class SemanticTriplet extends SemanticItem {
    public String candidateRef;
    public String subject;
    public String predicate;
    public String object;
    public String readable;
    public String subjectRef;
    public String objectRef;
}
