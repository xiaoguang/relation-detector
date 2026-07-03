package com.relationdetector.core.output;

import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Map;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.scan.ScanResult;

/**
 * dependency-free JSON 输出 writer。
 *
 * <p>CN: 输出层不引入 Jackson，手写稳定 JSON 结构。relationship 在 includeEvidence=true
 * 时输出 rawEvidence 与 evidence：rawEvidence 是未压缩审计轨迹，evidence 是
 * RelationshipMerger 聚合 repeated observation 后用于 confidence 的证据。
 *
 * <p>EN: Dependency-free JSON writer. It keeps the JSON schema stable without
 * Jackson. When evidence is requested, relationships write rawEvidence as the
 * full audit trail and evidence as the grouped evidence used by confidence scoring.
 */
public final class JsonResultWriter {
    /**
     * 将 ScanResult 渲染成最终 JSON 字符串。
     *
     * <p>EN: Renders ScanResult into the final JSON string.
     */
    public String write(ScanResult result, boolean includeEvidence, boolean includeWarnings) {
        StringBuilder out = new StringBuilder(4096);
        out.append("{\n");
        out.append("  \"database\": { \"type\": \"").append(escape(result.databaseType())).append("\", \"schema\": \"")
                .append(escape(result.schema())).append("\" },\n");
        out.append("  \"generatedAt\": \"").append(result.generatedAt()).append("\",\n");
        out.append("  \"summary\": { \"relationshipCount\": ").append(result.relationships().size())
                .append(", \"dataLineageCount\": ").append(result.dataLineages().size())
                .append(", \"namingEvidenceCount\": ").append(result.namingEvidence().size())
                .append(", \"warningCount\": ").append(result.warnings().size()).append(", \"sources\": ");
        writeStringArray(out, result.sources());
        out.append(" },\n");
        out.append("  \"relationships\": [\n");
        for (int i = 0; i < result.relationships().size(); i++) {
            writeRelationship(out, result.relationships().get(i), includeEvidence);
            if (i + 1 < result.relationships().size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  ],\n");
        out.append("  \"dataLineages\": [\n");
        for (int i = 0; i < result.dataLineages().size(); i++) {
            writeDataLineage(out, result.dataLineages().get(i), includeEvidence);
            if (i + 1 < result.dataLineages().size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  ],\n");
        out.append("  \"namingEvidence\": [\n");
        for (int i = 0; i < result.namingEvidence().size(); i++) {
            writeNamingEvidence(out, result.namingEvidence().get(i), includeEvidence);
            if (i + 1 < result.namingEvidence().size()) {
                out.append(",");
            }
            out.append("\n");
        }
        out.append("  ],\n");
        out.append("  \"warnings\": ");
        if (includeWarnings) {
            writeWarnings(out, result.warnings());
        } else {
            out.append("[]");
        }
        out.append("\n}\n");
        return out.toString();
    }

    private void writeRelationship(StringBuilder out, RelationshipCandidate relation, boolean includeEvidence) {
        out.append("    {\n");
        out.append("      \"source\": { \"table\": \"").append(escape(relation.source().table().displayName()))
                .append("\", \"column\": ");
        writeNullable(out, relation.source().isColumnLevel() ? relation.source().column().columnName() : null);
        out.append(" },\n");
        out.append("      \"target\": { \"table\": \"").append(escape(relation.target().table().displayName()))
                .append("\", \"column\": ");
        writeNullable(out, relation.target().isColumnLevel() ? relation.target().column().columnName() : null);
        out.append(" },\n");
        out.append("      \"relationType\": \"").append(relation.relationType()).append("\",\n");
        out.append("      \"relationSubType\": \"").append(relation.relationSubType()).append("\",\n");
        out.append("      \"confidence\": ").append(relation.confidence().setScale(4, RoundingMode.HALF_UP)).append(",\n");
        out.append("      \"rawEvidence\": ");
        if (includeEvidence) {
            writeEvidence(out, relation.rawEvidence().isEmpty() ? relation.evidence() : relation.rawEvidence());
        } else {
            out.append("[]");
        }
        out.append(",\n");
        out.append("      \"evidence\": ");
        if (includeEvidence) {
            writeEvidence(out, relation.evidence());
        } else {
            out.append("[]");
        }
        out.append(",\n      \"warnings\": ");
        writeWarnings(out, relation.warnings());
        out.append("\n    }");
    }

    private void writeDataLineage(StringBuilder out, DataLineageCandidate lineage, boolean includeEvidence) {
        out.append("    {\n");
        out.append("      \"sources\": [");
        for (int i = 0; i < lineage.sources().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append("{ \"table\": \"").append(escape(lineage.sources().get(i).table().displayName()))
                    .append("\", \"column\": ");
            writeNullable(out, lineage.sources().get(i).isColumnLevel()
                    ? lineage.sources().get(i).column().columnName() : null);
            out.append(" }");
        }
        out.append("],\n");
        out.append("      \"target\": { \"table\": \"").append(escape(lineage.target().table().displayName()))
                .append("\", \"column\": ");
        writeNullable(out, lineage.target().isColumnLevel() ? lineage.target().column().columnName() : null);
        out.append(" },\n");
        out.append("      \"flowKind\": \"").append(lineage.flowKind()).append("\",\n");
        out.append("      \"transformType\": \"").append(lineage.transformType()).append("\",\n");
        out.append("      \"confidence\": ").append(lineage.confidence().setScale(4, RoundingMode.HALF_UP)).append(",\n");
        out.append("      \"evidence\": ");
        if (includeEvidence) {
            writeDataLineageEvidence(out, lineage.evidence());
        } else {
            out.append("[]");
        }
        out.append(",\n      \"warnings\": ");
        writeWarnings(out, lineage.warnings());
        out.append(",\n      \"attributes\": ");
        writeAttributes(out, lineage.attributes());
        out.append("\n    }");
    }

    private void writeNamingEvidence(StringBuilder out, NamingEvidenceCandidate naming, boolean includeEvidence) {
        out.append("    {\n");
        out.append("      \"source\": ");
        writeEndpoint(out, naming.source());
        out.append(",\n      \"target\": ");
        writeEndpoint(out, naming.target());
        out.append(",\n");
        out.append("      \"rule\": \"").append(escape(naming.rule())).append("\",\n");
        out.append("      \"directionHint\": ").append(naming.directionHint()).append(",\n");
        out.append("      \"evidence\": ");
        if (includeEvidence) {
            writeEvidence(out, java.util.List.of(naming.evidence()));
        } else {
            out.append("[]");
        }
        out.append("\n    }");
    }

    private void writeEndpoint(StringBuilder out, Endpoint endpoint) {
        out.append("{ \"table\": \"").append(escape(endpoint.table().displayName())).append("\", \"column\": ");
        writeNullable(out, endpoint.isColumnLevel() ? endpoint.column().columnName() : null);
        out.append(" }");
    }

    private void writeEvidence(StringBuilder out, java.util.List<Evidence> evidence) {
        out.append("[");
        for (int i = 0; i < evidence.size(); i++) {
            Evidence item = evidence.get(i);
            out.append("\n        { \"type\": \"").append(item.type()).append("\", \"sourceType\": \"")
                    .append(item.sourceType()).append("\", \"score\": ").append(item.score())
                    .append(", \"source\": \"").append(escape(item.source())).append("\", \"detail\": \"")
                    .append(escape(item.detail())).append("\", \"attributes\": ");
            writeAttributes(out, item.attributes());
            out.append(" }");
            if (i + 1 < evidence.size()) {
                out.append(",");
            }
        }
        if (!evidence.isEmpty()) {
            out.append("\n      ");
        }
        out.append("]");
    }

    private void writeDataLineageEvidence(StringBuilder out, java.util.List<DataLineageEvidence> evidence) {
        out.append("[");
        for (int i = 0; i < evidence.size(); i++) {
            DataLineageEvidence item = evidence.get(i);
            out.append("\n        { \"transformType\": \"").append(item.transformType()).append("\", \"sourceType\": \"")
                    .append(item.sourceType()).append("\", \"score\": ").append(item.score())
                    .append(", \"source\": \"").append(escape(item.source())).append("\", \"detail\": \"")
                    .append(escape(item.detail())).append("\", \"attributes\": ");
            writeAttributes(out, item.attributes());
            out.append(" }");
            if (i + 1 < evidence.size()) {
                out.append(",");
            }
        }
        if (!evidence.isEmpty()) {
            out.append("\n      ");
        }
        out.append("]");
    }

    private void writeWarnings(StringBuilder out, java.util.List<WarningMessage> warnings) {
        out.append("[");
        for (int i = 0; i < warnings.size(); i++) {
            WarningMessage warning = warnings.get(i);
            out.append("{ \"type\": \"").append(warning.type()).append("\", \"severity\": \"")
                    .append(warning.severity()).append("\", \"code\": \"").append(escape(warning.code()))
                    .append("\", \"message\": \"").append(escape(warning.message())).append("\", \"source\": \"")
                    .append(escape(warning.source())).append("\", \"line\": ").append(warning.line())
                    .append(", \"attributes\": ");
            writeAttributes(out, warning.attributes());
            out.append(" }");
            if (i + 1 < warnings.size()) {
                out.append(", ");
            }
        }
        out.append("]");
    }

    private void writeAttributes(StringBuilder out, Map<String, Object> attributes) {
        out.append("{");
        Iterator<Map.Entry<String, Object>> iterator = attributes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            out.append("\"").append(escape(entry.getKey())).append("\": ");
            writeAttributeValue(out, entry.getValue());
            if (iterator.hasNext()) {
                out.append(", ");
            }
        }
        out.append("}");
    }

    /**
     * Writes metadata attributes without flattening all values into strings.
     *
     * <p>RelationshipMerger stores operator-facing attributes such as:
     *
     * <pre>{@code
     * count: 3
     * sampleTruncated: false
     * sampleDetails: ["line 10: o.user_id = u.id", "line 38: o.user_id = u.id"]
     * }</pre>
     *
     * Keeping these as JSON numbers, booleans, and arrays makes downstream
     * dashboards and tests consume them directly instead of reparsing strings.
     */
    private void writeAttributeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> mapValue) {
            out.append("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = mapValue.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                out.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\": ");
                writeAttributeValue(out, entry.getValue());
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
            out.append("}");
        } else if (value instanceof Iterable<?> values) {
            out.append("[");
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                writeAttributeValue(out, iterator.next());
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
            out.append("]");
        } else {
            out.append("\"").append(escape(String.valueOf(value))).append("\"");
        }
    }

    private void writeStringArray(StringBuilder out, java.util.List<String> values) {
        out.append("[");
        for (int i = 0; i < values.size(); i++) {
            out.append("\"").append(escape(values.get(i))).append("\"");
            if (i + 1 < values.size()) {
                out.append(", ");
            }
        }
        out.append("]");
    }

    private void writeNullable(StringBuilder out, String value) {
        if (value == null) {
            out.append("null");
        } else {
            out.append("\"").append(escape(value)).append("\"");
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
