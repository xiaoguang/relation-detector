package com.relationdetector.semantic.extract.model;

import java.util.List;

public final class SemanticLineage extends SemanticItem {
    public List<String> from;
    public List<String> fromPhysical;
    public String to;
    public String toPhysical;
    public String transform;
    public List<String> sourceEntityRefs;
    public String targetEntityRef;
}
