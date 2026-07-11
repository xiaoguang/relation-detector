package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.log.SourceNameNormalizer;

/**
 * DDL relationship 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 或 full-grammer DDL parser 输出的 DDL_FOREIGN_KEY /
 * DDL_INDEX 事件，并转换为 RelationshipCandidate。DDL parser 不直接创建最终关系。
 *
 * <p>EN: DDL relationship semantic extractor. It consumes DDL_FOREIGN_KEY and
 * DDL_INDEX events emitted by token-event or full-grammer DDL parsers and turns
 * them into RelationshipCandidate instances. DDL parsers do not create final
 * relationships directly.
 */
public final class DdlRelationExtractionVisitor {
    /**
     * 从 DDL structured events 生成 relationship 候选。
     *
     * <p>EN: Builds relationship candidates from DDL structured events.
     */
    public List<RelationshipCandidate> extract(String ddl, String sourceName, StructuredParseResult result) {
        DdlState state = new DdlState(SourceNameNormalizer.normalize(sourceName));
        for (StructuredSqlEvent event : result.events()) {
            if (event.type() == StructuredParseEventType.DDL_INDEX) {
                addIndex(event, state);
            }
        }
        for (StructuredSqlEvent event : result.events()) {
            if (event.type() == StructuredParseEventType.DDL_FOREIGN_KEY) {
                addForeignKey(event, state);
            }
        }
        state.enhanceCandidatesWithIndexes();
        return state.candidates();
    }

    private void addForeignKey(StructuredSqlEvent event, DdlState state) {
        TableId sourceTable = table(event.sourceTable());
        TableId targetTable = table(event.targetTable());
        String sourceColumn = clean(event.sourceColumn());
        String targetColumn = clean(event.targetColumn());
        if (sourceColumn.isBlank() || targetColumn.isBlank()) {
            return;
        }
        ColumnRef source = ColumnRef.of(sourceTable, sourceColumn);
        ColumnRef target = ColumnRef.of(targetTable, targetColumn);
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(source), Endpoint.column(target),
                RelationType.FK_LIKE, RelationSubType.DDL_DECLARED_FK);
        candidate.evidence().add(new Evidence(EvidenceType.DDL_FOREIGN_KEY,
                BigDecimal.valueOf(DefaultEvidenceScores.DDL_FOREIGN_KEY),
                EvidenceSourceType.DDL_FILE,
                state.source(),
                "token-event DDL foreign key",
                java.util.Map.of(
                        "compositePosition", event.compositePosition(),
                        "compositeSize", event.compositeSize())));
        state.addCandidate(candidate);
    }

    private void addIndex(StructuredSqlEvent event, DdlState state) {
        TableId table = table(event.table());
        String column = clean(event.column());
        String role = clean(event.role());
        if (column.isBlank()) {
            return;
        }
        if ("SOURCE_INDEX".equals(role)) {
            state.addSourceIndex(table, column);
        } else if ("TARGET_UNIQUE".equals(role)) {
            state.addTargetUnique(table, column);
        }
    }

    private TableId table(String raw) {
        List<String> parts = identifierParts(raw);
        String tableName = parts.isEmpty() ? clean(raw) : parts.get(parts.size() - 1);
        String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        return TableId.of(schema, tableName);
    }

    private List<String> identifierParts(String identifier) {
        List<String> parts = new ArrayList<>();
        if (identifier == null || identifier.isBlank()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if ((c == '"' || c == '`') && quote == 0) {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == quote) {
                quote = 0;
                current.append(c);
                continue;
            }
            if (c == '.' && quote == 0) {
                String part = clean(current.toString());
                if (!part.isBlank()) {
                    parts.add(part);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String part = clean(current.toString());
        if (!part.isBlank()) {
            parts.add(part);
        }
        return parts;
    }

    private String clean(String identifier) {
        if (identifier == null) {
            return "";
        }
        String value = identifier.trim();
        if ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record ColumnKey(String table, String column) {
        static ColumnKey of(TableId table, String column) {
            return new ColumnKey(table.normalizedName(), column.toLowerCase(Locale.ROOT));
        }
    }

    private record RelationshipKey(ColumnKey source, ColumnKey target) {
        static RelationshipKey of(RelationshipCandidate candidate) {
            return new RelationshipKey(
                    ColumnKey.of(candidate.source().table(), candidate.source().column().columnName()),
                    ColumnKey.of(candidate.target().table(), candidate.target().column().columnName()));
        }
    }

    private static final class DdlState {
        private final String source;
        private final List<RelationshipCandidate> candidates = new ArrayList<>();
        private final Map<RelationshipKey, RelationshipCandidate> candidatesByEndpoint = new LinkedHashMap<>();
        private final Set<ColumnKey> sourceIndexes = new LinkedHashSet<>();
        private final Set<ColumnKey> targetUnique = new LinkedHashSet<>();

        DdlState(String source) {
            this.source = source;
        }

        String source() {
            return source;
        }

        List<RelationshipCandidate> candidates() {
            return candidates;
        }

        void addCandidate(RelationshipCandidate candidate) {
            RelationshipKey key = RelationshipKey.of(candidate);
            RelationshipCandidate existing = candidatesByEndpoint.get(key);
            if (existing == null) {
                candidatesByEndpoint.put(key, candidate);
                candidates.add(candidate);
                return;
            }
            for (Evidence evidence : candidate.evidence()) {
                addEvidenceIfMissing(existing, evidence);
            }
        }

        void addSourceIndex(TableId table, String column) {
            sourceIndexes.add(ColumnKey.of(table, column));
        }

        void addTargetUnique(TableId table, String column) {
            targetUnique.add(ColumnKey.of(table, column));
        }

        void enhanceCandidatesWithIndexes() {
            for (RelationshipCandidate candidate : candidates) {
                ColumnKey sourceKey = ColumnKey.of(candidate.source().table(), candidate.source().column().columnName());
                ColumnKey targetKey = ColumnKey.of(candidate.target().table(), candidate.target().column().columnName());
                if (sourceIndexes.contains(sourceKey)) {
                    addEvidenceIfMissing(candidate, Evidence.of(EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                            EvidenceSourceType.DDL_FILE, source, "token-event DDL source-side index"));
                }
                if (targetUnique.contains(targetKey)) {
                    addEvidenceIfMissing(candidate, Evidence.of(EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                            EvidenceSourceType.DDL_FILE, source, "token-event DDL target-side primary/unique key"));
                }
            }
        }

        private void addEvidenceIfMissing(RelationshipCandidate candidate, Evidence evidence) {
            boolean exists = candidate.evidence().stream().anyMatch(existing -> existing.type() == evidence.type());
            if (!exists) {
                candidate.evidence().add(evidence);
            }
        }
    }
}
