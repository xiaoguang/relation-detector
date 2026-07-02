package com.relationdetector.mysql.log;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.DatabaseDdlCollector;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;
import com.relationdetector.contracts.spi.Collectors.MetadataCollector;
import com.relationdetector.contracts.spi.Collectors.ObjectDefinitionCollector;
import com.relationdetector.contracts.spi.Collectors.SqlLogExtractor;
import com.relationdetector.contracts.spi.Collectors.SqlRelationParser;
import com.relationdetector.contracts.spi.Collectors.StructuredDdlParser;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.contracts.spi.DatabaseAdaptor;
import com.relationdetector.contracts.parse.DatabaseDdlDefinition;
import com.relationdetector.contracts.parse.DatabaseObjectDefinition;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
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
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredDdlParser;
import com.relationdetector.mysql.tokenevent.MySqlTokenEventStructuredSqlParser;

/** MySQL 5.7/8.0 adaptor implementing the Phase 4 design. */

public final class MySqlLogExtractor implements SqlLogExtractor {
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
            StringBuilder current = new StringBuilder();
            long startLine = 1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("#") || line.startsWith("SET timestamp")) {
                    continue;
                }
                int queryIndex = line.indexOf(" Query ");
                String sql = queryIndex >= 0 ? line.substring(queryIndex + " Query ".length()) : line;
                if (sql.toLowerCase().contains("select") || sql.toLowerCase().contains("join")) {
                    if (current.isEmpty()) {
                        startLine = i + 1L;
                    }
                    current.append(sql).append('\n');
                    if (sql.trim().endsWith(";")) {
                        records.add(new SqlStatementRecord(current.toString(), StatementSourceType.NATIVE_LOG,
                                file.toString(), startLine, i + 1L, java.util.Map.of()));
                        current.setLength(0);
                    }
                }
            }
            if (!current.isEmpty()) {
                records.add(new SqlStatementRecord(current.toString(), StatementSourceType.NATIVE_LOG,
                        file.toString(), startLine, lines.size(), java.util.Map.of()));
            }
            return records.stream();
        } catch (Exception ex) {
            warnings.accept(DiagnosticWarnings.logExtractFailed(file, ex));
            return Stream.empty();
        }
    }
}
