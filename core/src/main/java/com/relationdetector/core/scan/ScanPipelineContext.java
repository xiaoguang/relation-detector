package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;

final class ScanPipelineContext {
    final ScanConfig config;
    final DatabaseAdaptor adaptor;
    final ScanScope scope;
    final ScanResult result;
    final AdaptorContext adaptorContext;
    final List<RelationshipCandidate> relationshipCandidates;
    final List<DataLineageCandidate> lineageCandidates;
    MetadataSnapshot metadataSnapshot;

    ScanPipelineContext(
            ScanConfig config,
            DatabaseAdaptor adaptor,
            ScanScope scope,
            ScanResult result,
            AdaptorContext adaptorContext,
            List<RelationshipCandidate> relationshipCandidates,
            List<DataLineageCandidate> lineageCandidates
    ) {
        this.config = config;
        this.adaptor = adaptor;
        this.scope = scope;
        this.result = result;
        this.adaptorContext = adaptorContext;
        this.relationshipCandidates = relationshipCandidates;
        this.lineageCandidates = lineageCandidates;
    }
}
