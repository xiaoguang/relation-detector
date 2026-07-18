package com.relationdetector.core.scan;

import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.ParserBundleSelector;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;

/**
 * CN: 为 scan 选择一致的 SQL/DDL parser bundle，并将 framed statements 交给正式 statement execution service。
 * EN: Selects one consistent SQL/DDL parser bundle for a scan and delegates framed statements to the production statement execution service.
 */
final class StatementParsePipeline {
    private final StatementExecutionService statementExecutionService = new StatementExecutionService();
    private final ParserBundleSelector parserBundleSelector = new ParserBundleSelector();

    ParserBundle selectedBundle(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            AdaptorContext context,
            ScanPipelineContext scanContext
    ) {
        return parserBundle(adaptor, config, context, scanContext);
    }

    StatementExecutionOutcome executeDdlStatements(
            ParserBundle parserBundle,
            DatabaseAdaptor adaptor,
            ScanConfig config,
            List<SqlStatementRecord> statements,
            AdaptorContext context
    ) {
        return statementExecutionService.executeDdlStatements(
                parserBundle, statements, EvidenceSourceType.DDL_FILE, context, config,
                adaptor.identifierRules(), namespace(context));
    }

    StatementExecutionOutcome executeDatabaseDdl(
            ParserBundle parserBundle,
            DatabaseAdaptor adaptor,
            ScanConfig config,
            DatabaseDdlDefinition definition,
            AdaptorContext context
    ) {
        NamespaceContext definitionNamespace = databaseDdlNamespace(context, definition);
        StatementExecutionOutcome outcome = statementExecutionService.executeDdlText(
                parserBundle, definition.ddl(), definition.source(), EvidenceSourceType.DATABASE_DDL, context, config,
                adaptor.identifierRules(), definitionNamespace);
        return new StatementExecutionOutcome(
                qualifyDatabaseDdlCandidates(
                        outcome.relationshipCandidates(), adaptor, definitionNamespace),
                outcome.lineageCandidates(),
                outcome.namingEvidence(),
                outcome.warnings(),
                outcome.ddlEvidenceInventory());
    }

    StatementExecutionOutcome executeStatement(
            ParserBundle parserBundle,
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables
    ) {
        return statementExecutionService.executeSql(
                adaptor, config, statement, context, knownPhysicalTables, parserBundle);
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
            String normalizedName = fact.schema() == null || fact.schema().isBlank()
                    ? fact.tableName()
                    : fact.schema() + "." + fact.tableName();
            tables.add(new TableId(fact.catalog(), fact.schema(), fact.tableName(), normalizedName));
        }
        return tables;
    }

    private List<RelationshipCandidate> qualifyDatabaseDdlCandidates(
            List<RelationshipCandidate> candidates,
            DatabaseAdaptor adaptor,
            NamespaceContext namespace
    ) {
        if (namespace.catalog().isBlank() && namespace.currentSchema().isBlank()) {
            return candidates;
        }
        CanonicalIdentifierResolver identifiers = new CanonicalIdentifierResolver(adaptor.identifierRules());
        return candidates.stream()
                .map(candidate -> qualifyDatabaseDdlCandidate(candidate, identifiers, namespace))
                .toList();
    }

    private RelationshipCandidate qualifyDatabaseDdlCandidate(
            RelationshipCandidate candidate,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        RelationshipCandidate qualified = new RelationshipCandidate(
                qualifyEndpoint(candidate.source(), identifiers, namespace),
                qualifyEndpoint(candidate.target(), identifiers, namespace),
                candidate.relationType(),
                candidate.relationSubType());
        qualified.confidence(candidate.confidence());
        qualified.evidence().addAll(candidate.evidence());
        qualified.rawEvidence().addAll(candidate.rawEvidence());
        qualified.warnings().addAll(candidate.warnings());
        return qualified;
    }

    private Endpoint qualifyEndpoint(
            Endpoint endpoint,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        TableId table = identifiers.resolve(endpoint.table(), namespace);
        if (!endpoint.isColumnLevel()) {
            return Endpoint.table(table);
        }
        ColumnRef column = endpoint.column();
        return Endpoint.column(new ColumnRef(table, column.columnName(), column.normalizedName(),
                column.dataType(), column.nullable()));
    }

    private NamespaceContext databaseDdlNamespace(
            AdaptorContext context,
            DatabaseDdlDefinition definition
    ) {
        if ((definition.catalog() != null && !definition.catalog().isBlank())
                || (definition.schema() != null && !definition.schema().isBlank())) {
            return new NamespaceContext(definition.catalog(), definition.schema(), List.of());
        }
        return namespace(context);
    }

    private NamespaceContext namespace(AdaptorContext context) {
        String catalog = context == null || context.scope() == null ? "" : context.scope().catalog();
        String scopeSchema = context == null || context.scope() == null ? "" : context.scope().schema();
        return new NamespaceContext(catalog, scopeSchema, List.of());
    }
}
