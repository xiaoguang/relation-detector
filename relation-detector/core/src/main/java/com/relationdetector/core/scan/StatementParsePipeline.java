package com.relationdetector.core.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.ParserBundleSelector;

final class StatementParsePipeline {
    private final StatementExecutionService statementExecutionService = new StatementExecutionService();
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();

    List<RelationshipCandidate> parseDdlFile(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context,
            ScanPipelineContext scanContext
    ) {
        try {
            StatementExecutionOutcome outcome = statementExecutionService.executeDdlFile(
                    parserBundle(adaptor, config, context, scanContext), file, context, config);
            scanContext.namingEvidencePool.addAll(outcome.namingEvidence());
            return outcome.relationshipCandidates();
        } catch (Exception ex) {
            scanContext.result.warnings().add(DiagnosticWarnings.ddlParseFailed(file, ex));
            return List.of();
        }
    }

    List<RelationshipCandidate> parseDatabaseDdl(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            DatabaseDdlDefinition definition,
            AdaptorContext context,
            ScanPipelineContext scanContext
    ) {
        try {
            StatementExecutionOutcome outcome = statementExecutionService.executeDdlText(
                    parserBundle(adaptor, config, context, scanContext),
                    definition.ddl(), definition.source(), EvidenceSourceType.DATABASE_DDL, context, config);
            scanContext.namingEvidencePool.addAll(outcome.namingEvidence());
            return qualifyDatabaseDdlCandidates(outcome.relationshipCandidates(), definition.schema());
        } catch (Exception ex) {
            scanContext.result.warnings().add(DiagnosticWarnings.ddlTextParseFailed(definition.source(), definition.ddl(), ex));
            return List.of();
        }
    }

    List<RelationshipCandidate> parseStatement(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ScanResult result,
            ScanPipelineContext scanContext,
            List<DataLineageCandidate> dataLineageCandidates,
            Set<TableId> knownPhysicalTables
    ) {
        try {
            StatementExecutionOutcome outcome = statementExecutionService.executeSql(
                    adaptor,
                    config,
                    statement,
                    context,
                    knownPhysicalTables,
                    parserBundle(adaptor, config, context, scanContext));
            dataLineageCandidates.addAll(outcome.lineageCandidates());
            scanContext.namingEvidencePool.addAll(outcome.namingEvidence());
            return outcome.relationshipCandidates();
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.sqlParseFailed(statement, ex));
            return List.of();
        }
    }

    private ParserBundle parserBundle(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            AdaptorContext context,
            ScanPipelineContext scanContext
    ) {
        if (scanContext.parserBundle == null) {
            scanContext.parserBundle = parserBundleSelector.select(adaptor, config, context);
        }
        return scanContext.parserBundle;
    }

    Set<TableId> knownPhysicalTables(MetadataSnapshot metadataSnapshot) {
        if (metadataSnapshot == null) {
            return Set.of();
        }
        Set<TableId> tables = new java.util.LinkedHashSet<>(metadataSnapshot.tables());
        for (MetadataTableFact fact : metadataSnapshot.tableFacts()) {
            tables.add(TableId.of(fact.schema(), fact.tableName()));
        }
        return tables;
    }

    private List<RelationshipCandidate> qualifyDatabaseDdlCandidates(
            List<RelationshipCandidate> candidates,
            String schema
    ) {
        if (schema == null || schema.isBlank()) {
            return candidates;
        }
        return candidates.stream()
                .map(candidate -> qualifyDatabaseDdlCandidate(candidate, schema))
                .toList();
    }

    private RelationshipCandidate qualifyDatabaseDdlCandidate(RelationshipCandidate candidate, String schema) {
        RelationshipCandidate qualified = new RelationshipCandidate(
                qualifyEndpoint(candidate.source(), schema),
                qualifyEndpoint(candidate.target(), schema),
                candidate.relationType(),
                candidate.relationSubType());
        qualified.confidence(candidate.confidence());
        qualified.evidence().addAll(candidate.evidence());
        qualified.rawEvidence().addAll(candidate.rawEvidence());
        qualified.warnings().addAll(candidate.warnings());
        return qualified;
    }

    private Endpoint qualifyEndpoint(Endpoint endpoint, String schema) {
        if (endpoint.table().schema() != null && !endpoint.table().schema().isBlank()) {
            return endpoint;
        }
        TableId table = TableId.of(schema, endpoint.table().tableName());
        if (!endpoint.isColumnLevel()) {
            return Endpoint.table(table);
        }
        ColumnRef column = endpoint.column();
        return Endpoint.column(new ColumnRef(table, column.columnName(), column.normalizedName(),
                column.dataType(), column.nullable()));
    }
}
