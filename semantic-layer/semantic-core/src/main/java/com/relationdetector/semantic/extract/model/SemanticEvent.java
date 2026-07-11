package com.relationdetector.semantic.extract.model;

import java.util.List;

public final class SemanticEvent extends SemanticItem {
    public String physicalName;
    public String eventCandidateRef;
    public List<String> inputs;
    public List<String> outputs;
    public List<String> inputEntityRefs;
    public List<String> outputEntityRefs;

    public String eventCandidateRef() {
        return eventCandidateRef;
    }
}
