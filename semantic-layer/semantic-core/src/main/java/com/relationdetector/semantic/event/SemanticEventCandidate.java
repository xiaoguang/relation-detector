package com.relationdetector.semantic.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CN: 保存由 write lineage 和 supporting relationships 确定生成的 event candidate、provenance、input/output endpoints 和 evidence refs；构造器冻结全部集合。
 * EN: Carries a deterministic event candidate grounded in write lineage and supporting relationships, including provenance, endpoints, and evidence references, with all collections frozen.
 */
public record SemanticEventCandidate(
        String id,
        String eventKind,
        String sourceType,
        String sourceObject,
        String sourceObjectType,
        String sourceObjectName,
        String sourceFile,
        String sourceStatementId,
        String readableNameHint,
        String businessActionHint,
        String eventNameBasis,
        List<String> operationKinds,
        List<String> inputEndpoints,
        List<String> outputEndpoints,
        List<String> lineageRefs,
        List<String> supportingDerivedLineageRefs,
        List<String> relationshipRefs,
        List<String> evidenceRefs,
        BigDecimal confidence,
        Map<String, Object> attributes
) {
    public SemanticEventCandidate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("event candidate id is required");
        }
        eventKind = eventKind == null || eventKind.isBlank() ? "SQL_WRITE_OPERATION" : eventKind;
        sourceType = sourceType == null || sourceType.isBlank() ? "SQL_WRITE" : sourceType;
        sourceObject = sourceObject == null ? "" : sourceObject;
        sourceObjectType = sourceObjectType == null || sourceObjectType.isBlank() ? sourceType : sourceObjectType;
        sourceObjectName = sourceObjectName == null ? "" : sourceObjectName;
        sourceFile = sourceFile == null ? "" : sourceFile;
        sourceStatementId = sourceStatementId == null ? "" : sourceStatementId;
        readableNameHint = readableNameHint == null ? "" : readableNameHint;
        businessActionHint = businessActionHint == null ? "" : businessActionHint;
        eventNameBasis = eventNameBasis == null ? "" : eventNameBasis;
        operationKinds = List.copyOf(operationKinds == null ? List.of() : operationKinds);
        inputEndpoints = List.copyOf(inputEndpoints == null ? List.of() : inputEndpoints);
        outputEndpoints = List.copyOf(outputEndpoints == null ? List.of() : outputEndpoints);
        lineageRefs = List.copyOf(lineageRefs == null ? List.of() : lineageRefs);
        supportingDerivedLineageRefs = List.copyOf(
                supportingDerivedLineageRefs == null ? List.of() : supportingDerivedLineageRefs);
        relationshipRefs = List.copyOf(relationshipRefs == null ? List.of() : relationshipRefs);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
