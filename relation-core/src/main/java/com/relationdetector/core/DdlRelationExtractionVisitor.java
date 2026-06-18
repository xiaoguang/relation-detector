package com.relationdetector.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Converts structured ANTLR DDL events into relationship candidates.
 *
 * <p>This visitor is independent from {@link SimpleDdlParser}. It consumes only
 * {@link StructuredSqlEvent} facts produced by {@link DdlStructuredEventVisitor}
 * and intentionally mirrors the same conservative evidence semantics.
 */
public final class DdlRelationExtractionVisitor {
    public List<RelationshipCandidate> extract(String ddl, String sourceName, StructuredParseResult result) {
        DdlState state = new DdlState(sourceName);
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
        TableId sourceTable = table(text(event, "sourceTable"));
        TableId targetTable = table(text(event, "targetTable"));
        String sourceColumn = text(event, "sourceColumn");
        String targetColumn = text(event, "targetColumn");
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
                "ANTLR DDL foreign key",
                java.util.Map.of(
                        "compositePosition", intValue(event, "compositePosition", 1),
                        "compositeSize", intValue(event, "compositeSize", 1))));
        state.candidates().add(candidate);
    }

    private void addIndex(StructuredSqlEvent event, DdlState state) {
        TableId table = table(text(event, "table"));
        String column = text(event, "column");
        String role = text(event, "role");
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

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : clean(String.valueOf(value));
    }

    private int intValue(StructuredSqlEvent event, String key, int fallback) {
        Object value = event.attributes().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
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

    private static final class DdlState {
        private final String source;
        private final List<RelationshipCandidate> candidates = new ArrayList<>();
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
                    candidate.evidence().add(Evidence.of(EvidenceType.SOURCE_INDEX, DefaultEvidenceScores.SOURCE_INDEX,
                            EvidenceSourceType.DDL_FILE, source, "ANTLR DDL source-side index"));
                }
                if (targetUnique.contains(targetKey)) {
                    candidate.evidence().add(Evidence.of(EvidenceType.TARGET_UNIQUE, DefaultEvidenceScores.TARGET_UNIQUE,
                            EvidenceSourceType.DDL_FILE, source, "ANTLR DDL target-side primary/unique key"));
                }
            }
        }
    }
}
