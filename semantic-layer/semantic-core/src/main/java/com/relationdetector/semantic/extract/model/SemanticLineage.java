package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 表示物理字段与语义实体均可追溯的语义 lineage，承载 source/target、transform 和解析引用；所有物理列必须由 evidence bundle 证明。
 * EN: Represents semantic lineage traceable through physical fields and semantic entities, carrying sources, target, transform, and resolved references. Every physical column must exist in the evidence bundle.
 */
public final class SemanticLineage extends SemanticItem {
    public List<String> from;
    public List<String> fromPhysical;
    public String to;
    public String toPhysical;
    public String transform;
    public List<String> sourceEntityRefs;
    public String targetEntityRef;
}
