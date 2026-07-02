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
import com.relationdetector.core.lineage.TokenEventDataLineageExtractor;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.parser.SqlRelationParserRunner;

final class StatementParsePipeline {
    private final SqlRelationParserRunner sqlParserRunner = new SqlRelationParserRunner();
    private final DdlRelationParserRunner ddlParserRunner = new DdlRelationParserRunner();
    private final TokenEventDataLineageExtractor dataLineageExtractor = new TokenEventDataLineageExtractor();

    List<RelationshipCandidate> parseDdlFile(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            Path file,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            return ddlParserRunner.parse(adaptor, config, file, context);
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.ddlParseFailed(file, ex));
            return List.of();
        }
    }

    List<RelationshipCandidate> parseDatabaseDdl(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            DatabaseDdlDefinition definition,
            AdaptorContext context,
            ScanResult result
    ) {
        try {
            List<RelationshipCandidate> parsed = ddlParserRunner.parseText(adaptor, config,
                    definition.ddl(), definition.source(), EvidenceSourceType.DATABASE_DDL, context);
            return qualifyDatabaseDdlCandidates(parsed, definition.schema());
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.ddlTextParseFailed(definition.source(), definition.ddl(), ex));
            return List.of();
        }
    }

    List<RelationshipCandidate> parseStatement(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            ScanResult result,
            List<DataLineageCandidate> dataLineageCandidates,
            Set<TableId> knownPhysicalTables
    ) {
        try {
            var parsed = sqlParserRunner.parseStructuredAndRelations(adaptor, config, statement, context);
            parsed.structured().ifPresent(structured ->
                    dataLineageCandidates.addAll(dataLineageExtractor.extract(statement, structured, knownPhysicalTables)));
            return parsed.relationships();
        } catch (Exception ex) {
            result.warnings().add(DiagnosticWarnings.sqlParseFailed(statement, ex));
            return List.of();
        }
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
