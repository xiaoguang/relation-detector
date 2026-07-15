package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.ddl.DdlEvidenceInventory;
import com.relationdetector.core.identity.CanonicalEndpointKey;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;

/**
 * DDL relationship 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 或 full-grammar DDL parser 输出的 DDL_FOREIGN_KEY /
 * DDL_INDEX 事件，并转换为 RelationshipCandidate。DDL parser 不直接创建最终关系。
 *
 * <p>EN: DDL relationship semantic extractor. It consumes DDL_FOREIGN_KEY and
 * DDL_INDEX events emitted by token-event or full-grammar DDL parsers and turns
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
        return extract(ddl, sourceName, result, defaultIdentifierRules(), NamespaceContext.empty());
    }

    public List<RelationshipCandidate> extract(
            String ddl,
            String sourceName,
            StructuredParseResult result,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        DdlState state = new DdlState(SourceNameNormalizer.normalize(sourceName), identifierRules, namespace);
        for (StructuredSqlEvent event : result.events()) {
            if (event.type() == StructuredParseEventType.DDL_FOREIGN_KEY) {
                addForeignKey(event, state);
            }
        }
        inventory(result.events(), EvidenceSourceType.DDL_FILE, sourceName, identifierRules, namespace)
                .enhance(state.candidates());
        return state.candidates();
    }

    public DdlEvidenceInventory inventory(
            List<StructuredSqlEvent> events,
            EvidenceSourceType sourceType,
            String sourceName
    ) {
        return inventory(events, sourceType, sourceName, defaultIdentifierRules(), NamespaceContext.empty());
    }

    public DdlEvidenceInventory inventory(
            List<StructuredSqlEvent> events,
            EvidenceSourceType sourceType,
            String sourceName,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        DdlEvidenceInventory inventory = new DdlEvidenceInventory(identifierRules, namespace);
        CanonicalIdentifierResolver resolver = new CanonicalIdentifierResolver(identifierRules);
        for (StructuredSqlEvent event : events == null ? List.<StructuredSqlEvent>of() : events) {
            if (event.type() != StructuredParseEventType.DDL_INDEX
                    && event.type() != StructuredParseEventType.DDL_COLUMN) {
                continue;
            }
            TableId table = table(event.table());
            String column = clean(event.column());
            if (column.isBlank()) {
                continue;
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            copyProvenance(event, attributes);
            String source = event.sourceName().isBlank()
                    ? SourceNameNormalizer.normalize(sourceName)
                    : SourceNameNormalizer.normalize(event.sourceName());
            DdlEvidenceInventory.Observation observation = new DdlEvidenceInventory.Observation(
                    event.role(), event.kind(), sourceType, source, event.line(), attributes);
            CanonicalEndpointKey key = CanonicalEndpointKey.from(
                    Endpoint.column(ColumnRef.of(table, column)), resolver, namespace);
            if (event.type() == StructuredParseEventType.DDL_COLUMN) {
                inventory.addColumn(key, observation);
            } else if (event.compositeSize() > 1) {
                // Composite keys/indexes describe an ordered column group. A member
                // endpoint is not independently indexed or unique for relationship
                // direction inference.
                continue;
            } else if ("SOURCE_INDEX".equals(clean(event.role()))) {
                inventory.addSourceIndex(key, observation);
            } else if ("TARGET_UNIQUE".equals(clean(event.role()))) {
                inventory.addTargetUnique(key, observation);
            }
        }
        return inventory;
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
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("compositePosition", event.compositePosition());
        attributes.put("compositeSize", event.compositeSize());
        copyProvenance(event, attributes);
        candidate.evidence().add(new Evidence(EvidenceType.DDL_FOREIGN_KEY,
                BigDecimal.valueOf(DefaultEvidenceScores.DDL_FOREIGN_KEY),
                EvidenceSourceType.DDL_FILE,
                event.sourceName().isBlank() ? state.source() : SourceNameNormalizer.normalize(event.sourceName()),
                "token-event DDL foreign key",
                attributes));
        state.addCandidate(candidate);
    }

    private void copyProvenance(StructuredSqlEvent event, Map<String, Object> attributes) {
        attributes.put("sourceLine", event.line());
        putIfPresent(attributes, "sourceFile", event.provenance().sourceFile());
        putIfPresent(attributes, "sourceStatementId", event.provenance().sourceStatementId());
        putIfPresent(attributes, "sourceBlockId", event.provenance().sourceBlockId());
        putIfPresent(attributes, "sourceObjectType", event.provenance().sourceObjectType());
        putIfPresent(attributes, "sourceObjectName", event.provenance().sourceObjectName());
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private TableId table(String raw) {
        List<String> parts = identifierParts(raw);
        String tableName = parts.isEmpty() ? clean(raw) : parts.get(parts.size() - 1);
        String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : null;
        String catalog = parts.size() > 2
                ? String.join(".", parts.subList(0, parts.size() - 2))
                : null;
        String normalizedName = schema == null || schema.isBlank()
                ? tableName
                : schema + "." + tableName;
        return new TableId(catalog, schema, tableName, normalizedName);
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
        if ((value.startsWith("`") && value.endsWith("`"))
                || (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("[") && value.endsWith("]"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record RelationshipKey(CanonicalEndpointKey source, CanonicalEndpointKey target) {
        static RelationshipKey of(
                RelationshipCandidate candidate,
                CanonicalIdentifierResolver resolver,
                NamespaceContext namespace
        ) {
            return new RelationshipKey(
                    CanonicalEndpointKey.from(candidate.source(), resolver, namespace),
                    CanonicalEndpointKey.from(candidate.target(), resolver, namespace));
        }
    }

    private static final class DdlState {
        private final String source;
        private final CanonicalIdentifierResolver resolver;
        private final NamespaceContext namespace;
        private final List<RelationshipCandidate> candidates = new ArrayList<>();
        private final Map<RelationshipKey, RelationshipCandidate> candidatesByEndpoint = new LinkedHashMap<>();

        DdlState(String source, IdentifierRules identifierRules, NamespaceContext namespace) {
            this.source = source;
            this.resolver = new CanonicalIdentifierResolver(identifierRules);
            this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
        }

        String source() {
            return source;
        }

        List<RelationshipCandidate> candidates() {
            return candidates;
        }

        void addCandidate(RelationshipCandidate candidate) {
            RelationshipKey key = RelationshipKey.of(candidate, resolver, namespace);
            RelationshipCandidate existing = candidatesByEndpoint.get(key);
            if (existing == null) {
                candidatesByEndpoint.put(key, candidate);
                candidates.add(candidate);
                return;
            }
            for (Evidence evidence : candidate.evidence()) {
                existing.evidence().add(evidence);
            }
        }
    }

    private static IdentifierRules defaultIdentifierRules() {
        return value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
