package com.relationdetector.core.scan;

import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.parser.DdlParseOutcome;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.core.naming.NamingEvidenceExtractor;
import com.relationdetector.core.relation.StructuredRelationshipExtractor;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.provenance.SourceProvenanceValidator;

/**
 * Executes one SQL or DDL statement through the same structured parser,
 * relationship, lineage, and naming-evidence path used by production scans.
 */
public final class StatementExecutionService {
    private final SqlRelationParserRunner sqlParserRunner = new SqlRelationParserRunner();
    private final DdlRelationParserRunner ddlParserRunner = new DdlRelationParserRunner();
    private final StructuredDataLineageExtractor dataLineageExtractor = new StructuredDataLineageExtractor();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final StructuredRelationshipExtractor tokenEventRelationExtractor = new StructuredRelationshipExtractor();
    private final SourceProvenanceValidator provenanceValidator = new SourceProvenanceValidator();

    public StatementExecutionOutcome executeSql(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables
    ) {
        return executeSql(adaptor, config, statement, context, knownPhysicalTables, null);
    }

    public StatementExecutionOutcome executeSql(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables,
            ParserBundle parserBundle
    ) {
        SqlRelationParserRunner.ParsedSqlRelations parsed = parserBundle == null
                || adaptor.parsers().structuredSql().isEmpty()
                ? sqlParserRunner.parseStructuredAndRelations(adaptor, config, statement, context)
                : sqlParserRunner.parseStructuredAndRelations(
                        config, statement, context, parserBundle, adaptor.identifierRules());
        List<DataLineageCandidate> lineages = parsed.structured()
                .map(structured -> new StructuredDataLineageExtractor(
                        adaptor.identifierRules(), namespace(context))
                        .extract(statement, structured, knownPhysicalTables))
                .orElseGet(List::of);
        List<NamingEvidenceCandidate> namingEvidence =
                namingEvidenceExtractor.extractFromRelationshipCandidates(parsed.relationships(), config);
        return new StatementExecutionOutcome(parsed.relationships(), lineages, namingEvidence, List.of());
    }

    public StatementExecutionOutcome executeSql(
            StructuredSqlParser parser,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables
    ) {
        return executeSql(parser, statement, context, knownPhysicalTables, null);
    }

    public StatementExecutionOutcome executeSql(
            StructuredSqlParser parser,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables,
            ScanConfig config
    ) {
        StructuredParseResult structured = parser.parseSql(statement, context);
        List<RelationshipCandidate> relationships = tokenEventRelationExtractor.extract(statement, structured);
        List<DataLineageCandidate> lineages =
                dataLineageExtractor.extract(statement, structured, knownPhysicalTables);
        List<NamingEvidenceCandidate> namingEvidence =
                namingEvidenceExtractor.extractFromRelationshipCandidates(relationships, config);
        return new StatementExecutionOutcome(
                relationships, lineages, namingEvidence, provenanceValidator.validate(statement, structured));
    }

    public StatementExecutionOutcome executeDdlText(
            DatabaseAdaptor adaptor,
            ScanConfig config,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(adaptor, config, ddl, sourceName, sourceType, context));
    }

    public StatementExecutionOutcome executeDdlText(
            ParserBundle parserBundle,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(parserBundle, ddl, sourceName, sourceType, context));
    }

    public StatementExecutionOutcome executeDdlText(
            ParserBundle parserBundle,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(parserBundle, ddl, sourceName, sourceType, context,
                config));
    }

    public StatementExecutionOutcome executeDdlText(
            ParserBundle parserBundle,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(
                parserBundle.ddlParser(), ddl, sourceName, sourceType, context, config,
                identifierRules, namespace));
    }

    public StatementExecutionOutcome executeDdlText(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(parser, ddl, sourceName, sourceType, context));
    }

    public StatementExecutionOutcome executeDdlText(
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(parser, ddl, sourceName, sourceType, context, config));
    }

    public StatementExecutionOutcome executeDdlStatements(
            ParserBundle parserBundle,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return ddlOutcome(ddlParserRunner.parseStatementsWithEvidence(
                parserBundle, statements, sourceType, context, config));
    }

    public StatementExecutionOutcome executeDdlStatements(
            ParserBundle parserBundle,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        return ddlOutcome(ddlParserRunner.parseStatementsWithEvidence(
                parserBundle, statements, sourceType, context, config, identifierRules, namespace));
    }

    public StatementExecutionOutcome executeDdlStatements(
            StructuredDdlParser parser,
            List<SqlStatementRecord> statements,
            EvidenceSourceType sourceType,
            AdaptorContext context,
            ScanConfig config
    ) {
        return ddlOutcome(ddlParserRunner.parseStatementsWithEvidence(
                parser, statements, sourceType, context, config));
    }

    private StatementExecutionOutcome ddlOutcome(DdlParseOutcome parsed) {
        return new StatementExecutionOutcome(
                parsed.relationships(),
                List.of(),
                parsed.namingEvidence(),
                List.of(),
                parsed.inventory());
    }

    private NamespaceContext namespace(AdaptorContext context) {
        if (context == null || context.scope() == null) {
            return NamespaceContext.empty();
        }
        return new NamespaceContext(context.scope().catalog(), context.scope().schema(), List.of());
    }
}
