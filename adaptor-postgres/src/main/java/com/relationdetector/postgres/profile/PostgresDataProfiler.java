package com.relationdetector.postgres.profile;


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

public final class PostgresDataProfiler implements DataProfiler {
    @Override
    public List<Evidence> profile(Connection connection, ProfileRequest request) {
        RelationshipCandidate c = request.candidate();
        if (!c.source().isColumnLevel() || !c.target().isColumnLevel()) {
            return List.of();
        }
        String sql = "SELECT COUNT(*) FROM (SELECT DISTINCT " + c.source().column().columnName()
                + " AS v FROM " + c.source().table().displayName()
                + " WHERE " + c.source().column().columnName() + " IS NOT NULL LIMIT " + request.sampleRows()
                + ") s JOIN " + c.target().table().displayName()
                + " t ON s.v = t." + c.target().column().columnName();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(request.timeoutSeconds());
            try (ResultSet rs = statement.executeQuery(sql)) {
                if (rs.next() && rs.getLong(1) > 0) {
                    Evidence evidence = new Evidence(EvidenceType.VALUE_OVERLAP_HIGH, new BigDecimal("0.20"),
                            EvidenceSourceType.DATA_PROFILE, "postgres-data-profile",
                            "sampled source values matched target values",
                            java.util.Map.of("matched", rs.getLong(1), "sampleRows", request.sampleRows()));
                    return List.of(evidence);
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }
}
