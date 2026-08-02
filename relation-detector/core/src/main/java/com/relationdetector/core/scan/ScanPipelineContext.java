package com.relationdetector.core.scan;

import java.util.List;

import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.parser.runtime.ParserBundle;
import com.relationdetector.core.naming.NamingEvidencePool;
import com.relationdetector.core.ddl.DdlEvidenceInventory;
import com.relationdetector.core.ddl.DdlCatalogInventory;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.config.ResolvedScanConfig;
import com.relationdetector.core.config.ScanConfig;
import com.relationdetector.core.execution.ScanTaskExecutor;
import com.relationdetector.core.result.ScanResult;

/**
 * CN: 持有一次scan中各阶段共享且生命周期一致的配置、validated adaptor、候选池、inventory与任务执行器；
 * 输入由ScanEngine在预检后创建，输出是collector/parser/evidence阶段共同更新的scan状态。上游是ScanEngine，
 * 下游是各pipeline；本类不解析SQL、不决定事实语义，并在关闭时只负责释放scan级执行器。
 *
 * <p>EN: Holds configuration, validated adaptor, candidate pools, inventories, and task executor that share one scan
 * lifecycle. ScanEngine creates it after preflight and pipelines update the contained scan state. It neither parses
 * SQL nor decides fact semantics, and closing it only releases scan-scoped execution resources.
 */
public final class ScanPipelineContext implements AutoCloseable {
    public final ResolvedScanConfig config;
    public final ScanConfig parserConfig;
    public final DatabaseAdaptor adaptor;
    public final ScanScope scope;
    public final ScanResult result;
    public final AdaptorContext adaptorContext;
    public final List<RelationshipCandidate> relationshipCandidates;
    public final List<DataLineageCandidate> lineageCandidates;
    public final CanonicalEndpointKeyProvider endpointKeys;
    public final NamingEvidencePool namingEvidencePool;
    public final DdlEvidenceInventory ddlEvidenceInventory;
    public final DdlCatalogInventory ddlCatalogInventory;
    public final ScanTaskExecutor taskExecutor;
    public ParserBundle parserBundle;
    public MetadataSnapshot metadataSnapshot;
    public MetadataSnapshot physicalInventorySnapshot;

    public ScanPipelineContext(
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
        NamespaceContext namespace = new NamespaceContext(scope.catalog(), scope.schema(), List.of());
        this.endpointKeys = new CanonicalEndpointKeyProvider(adaptor.identifierRules(), namespace);
        this.namingEvidencePool = new NamingEvidencePool(endpointKeys);
        this.ddlEvidenceInventory = new DdlEvidenceInventory(
                adaptor.identifierRules(), namespace);
        this.ddlCatalogInventory = new DdlCatalogInventory();
        this.taskExecutor = new ScanTaskExecutor(config.execution().parallelism());
    }

    @Override
    public void close() {
        taskExecutor.close();
    }
}
