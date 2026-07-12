package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.ddl.DdlEvidenceInventory;
import com.relationdetector.core.identity.NamespaceContext;

final class ScanPipelineContext implements AutoCloseable {
    final ResolvedScanConfig config;
    final ScanConfig parserConfig;
    final DatabaseAdaptor adaptor;
    final ScanScope scope;
    final ScanResult result;
    final AdaptorContext adaptorContext;
    final List<RelationshipCandidate> relationshipCandidates;
    final List<DataLineageCandidate> lineageCandidates;
    final NamingEvidencePool namingEvidencePool;
    final DdlEvidenceInventory ddlEvidenceInventory;
    final ScanTaskExecutor taskExecutor;
    ParserBundle parserBundle;
    MetadataSnapshot metadataSnapshot;

    ScanPipelineContext(
            ResolvedScanConfig config,
            DatabaseAdaptor adaptor,
            ScanScope scope,
            ScanResult result,
            AdaptorContext adaptorContext,
            List<RelationshipCandidate> relationshipCandidates,
            List<DataLineageCandidate> lineageCandidates
    ) {
        this.config = config;
        this.parserConfig = config.parserCompatibilityView();
        this.adaptor = adaptor;
        this.scope = scope;
        this.result = result;
        this.adaptorContext = adaptorContext;
        this.relationshipCandidates = relationshipCandidates;
        this.lineageCandidates = lineageCandidates;
        this.namingEvidencePool = new NamingEvidencePool();
        this.ddlEvidenceInventory = new DdlEvidenceInventory(
                adaptor.identifierRules(),
                new NamespaceContext(scope.catalog(), scope.schema(), List.of()));
        this.taskExecutor = new ScanTaskExecutor(config.execution().parallelism());
    }

    @Override
    public void close() {
        taskExecutor.close();
    }
}
