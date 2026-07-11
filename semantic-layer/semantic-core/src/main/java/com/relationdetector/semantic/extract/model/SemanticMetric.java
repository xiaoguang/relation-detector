package com.relationdetector.semantic.extract.model;

import java.util.List;

public final class SemanticMetric extends SemanticItem {
    public String physicalField;
    public List<String> sourceFields;
    public String ownerEntityRef;
    public List<String> sourceEntityRefs;
}
