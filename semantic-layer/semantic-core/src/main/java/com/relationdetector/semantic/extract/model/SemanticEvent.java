package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 表示由 event candidate 支撑的业务事件及其输入/输出实体引用；正式 normalization 必须解析 candidate 和 evidence，本 DTO 不创造事件事实。
 * EN: Represents a business event backed by an event candidate with input and output entity references. Formal normalization resolves candidate and evidence; this DTO creates no event facts.
 */
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
