package com.relationdetector.postgres.log;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.AdaptorCapability;
import com.relationdetector.contracts.Enums.DatabaseObjectType;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.LogFormatHint;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.core.diagnostics.DiagnosticWarnings;
import com.relationdetector.core.log.PlainSqlLogExtractor;
import com.relationdetector.core.relation.TokenEventSqlRelationParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredDdlParser;
import com.relationdetector.postgres.tokenevent.PostgresTokenEventStructuredSqlParser;

/** PostgreSQL 12+ adaptor implementing the Phase 5 design. */

public final class PostgresLogExtractor implements SqlLogExtractor {
    @Override
    public Stream<SqlStatementRecord> extract(Path file, LogFormatHint hint) {
        return extract(file, hint, warning -> {
        });
    }

    @Override
    public Stream<SqlStatementRecord> extract(
            Path file,
            LogFormatHint hint,
            Consumer<WarningMessage> warnings
    ) {
        if (hint == LogFormatHint.PLAIN_SQL) {
            return new PlainSqlLogExtractor().extract(file, StatementSourceType.PLAIN_SQL, warnings);
        }
        try {
            List<SqlStatementRecord> records = new ArrayList<>();
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int statement = line.indexOf("statement:");
                int execute = line.indexOf("execute ");
                String sql = null;
                if (statement >= 0) {
                    sql = line.substring(statement + "statement:".length()).trim();
                } else if (execute >= 0 && line.contains(":")) {
                    sql = line.substring(line.indexOf(':', execute) + 1).trim();
                }
                if (sql != null && !sql.isBlank()) {
                    records.add(new SqlStatementRecord(sql, StatementSourceType.NATIVE_LOG,
                            file.toString(), i + 1L, i + 1L, java.util.Map.of()));
                }
            }
            return records.stream();
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }
}
