package com.relationdetector.core.scan;

import java.nio.file.Path;
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
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.parser.DdlParseOutcome;
import com.relationdetector.core.parser.DdlRelationParserRunner;
import com.relationdetector.core.parser.ParserBundle;
import com.relationdetector.core.parser.SqlRelationParserRunner;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

/**
 * Executes one SQL or DDL statement through the same structured parser,
 * relationship, lineage, and naming-evidence path used by production scans.
 */
public final class StatementExecutionService {
    private final SqlRelationParserRunner sqlParserRunner = new SqlRelationParserRunner();
    private final DdlRelationParserRunner ddlParserRunner = new DdlRelationParserRunner();
    private final StructuredDataLineageExtractor dataLineageExtractor = new StructuredDataLineageExtractor();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final TokenEventRelationExtractor tokenEventRelationExtractor = new TokenEventRelationExtractor();

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
                : sqlParserRunner.parseStructuredAndRelations(config, statement, context, parserBundle);
        List<DataLineageCandidate> lineages = parsed.structured()
                .map(structured -> dataLineageExtractor.extract(statement, structured, knownPhysicalTables))
                .orElseGet(List::of);
        List<NamingEvidenceCandidate> namingEvidence =
                namingEvidenceExtractor.extractFromRelationshipCandidates(parsed.relationships());
        return new StatementExecutionOutcome(parsed.relationships(), lineages, namingEvidence, List.of());
    }

    public StatementExecutionOutcome executeSql(
            StructuredSqlParser parser,
            SqlStatementRecord statement,
            AdaptorContext context,
            Set<TableId> knownPhysicalTables
    ) {
        StructuredParseResult structured = parser.parseSql(statement, context);
        List<RelationshipCandidate> relationships = tokenEventRelationExtractor.extract(statement, structured);
        List<DataLineageCandidate> lineages =
                dataLineageExtractor.extract(statement, structured, knownPhysicalTables);
        List<NamingEvidenceCandidate> namingEvidence =
                namingEvidenceExtractor.extractFromRelationshipCandidates(relationships);
        return new StatementExecutionOutcome(relationships, lineages, namingEvidence, List.of());
    }

    public StatementExecutionOutcome executeDdlFile(
            ParserBundle parserBundle,
            Path file,
            AdaptorContext context
    ) {
        DdlParseOutcome parsed = ddlParserRunner.parseWithEvidence(parserBundle, file, context);
        return ddlOutcome(parsed);
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
            StructuredDdlParser parser,
            String ddl,
            String sourceName,
            EvidenceSourceType sourceType,
            AdaptorContext context
    ) {
        return ddlOutcome(ddlParserRunner.parseTextWithEvidence(parser, ddl, sourceName, sourceType, context));
    }

    private StatementExecutionOutcome ddlOutcome(DdlParseOutcome parsed) {
        return new StatementExecutionOutcome(
                parsed.relationships(),
                List.of(),
                parsed.namingEvidence(),
                List.of());
    }
}
