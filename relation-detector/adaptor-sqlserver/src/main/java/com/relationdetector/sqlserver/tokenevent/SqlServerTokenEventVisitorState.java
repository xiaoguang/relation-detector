package com.relationdetector.sqlserver.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;

/** Per-parse state and identifier/scope helpers for SQL Server token-event traversal. */
abstract class SqlServerTokenEventVisitorState extends SqlServerRelationSqlBaseVisitor<Void> {
    protected final Map<String, LineageTransformType> functionExtensions = Map.of(
            "isnull", LineageTransformType.COALESCE);
    protected final boolean ddlOnly;
    protected final TokenEventEventEmitter emitter;
    protected final List<StructuredSqlEvent> events = new ArrayList<>();
    protected final ArrayDeque<String> projectionOwners = new ArrayDeque<>();
    protected final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    protected final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    protected final ArrayDeque<String> statementScopes = new ArrayDeque<>();
    protected int existsDepth;
    protected int nextStatementScope = 1;

    SqlServerTokenEventVisitorState(SqlStatementRecord statement, boolean ddlOnly) {
        this.ddlOnly = ddlOnly;
        this.emitter = new TokenEventEventEmitter(statement,
                type -> !ddlOnly
                        || type == StructuredParseEventType.DDL_FOREIGN_KEY
                        || type == StructuredParseEventType.DDL_INDEX
                        || type == StructuredParseEventType.DDL_COLUMN,
                () -> statementScopes.isEmpty() ? "" : statementScopes.peek());
    }

    protected String outputColumn(SqlServerRelationSqlParser.Select_list_elemContext item) {
        if (item.as_column_alias() != null) return clean(item.as_column_alias().id_().getText());
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected String singleProjectionQualifier(SqlServerRelationSqlParser.Table_sourcesContext tableSources) {
        if (tableSources == null || tableSources.table_source().size() != 1) return "";
        SqlServerRelationSqlParser.Table_sourceContext source = tableSources.table_source(0);
        return source.table_source_suffix().isEmpty() ? rowsetAlias(source.table_source_item()) : "";
    }

    protected String rowsetAlias(SqlServerRelationSqlParser.Table_source_itemContext item) {
        if (item.as_table_alias() != null) return clean(item.as_table_alias().table_alias().getText());
        return item.full_table_name() == null ? "" : baseName(qualifiedName(item.full_table_name()));
    }

    protected String tableForAlias(SqlServerRelationSqlParser.Table_sourcesContext sources, String aliasOrTable) {
        String target = clean(aliasOrTable);
        if (sources == null) return aliasOrTable;
        for (SqlServerRelationSqlParser.Table_source_itemContext item : tableSourceItems(sources)) {
            if (item.full_table_name() == null) continue;
            String table = qualifiedName(item.full_table_name());
            String alias = item.as_table_alias() == null ? ""
                    : clean(item.as_table_alias().table_alias().getText());
            if (target.equalsIgnoreCase(alias) || target.equalsIgnoreCase(baseName(table))) return table;
        }
        return aliasOrTable;
    }

    private List<SqlServerRelationSqlParser.Table_source_itemContext> tableSourceItems(
            SqlServerRelationSqlParser.Table_sourcesContext sources) {
        List<SqlServerRelationSqlParser.Table_source_itemContext> items = new ArrayList<>();
        for (SqlServerRelationSqlParser.Table_sourceContext source : sources.table_source()) {
            collectTableSourceItems(source, items);
        }
        return items;
    }

    private void collectTableSourceItems(SqlServerRelationSqlParser.Table_sourceContext source,
            List<SqlServerRelationSqlParser.Table_source_itemContext> items) {
        items.add(source.table_source_item());
        for (SqlServerRelationSqlParser.Table_source_suffixContext suffix : source.table_source_suffix()) {
            if (suffix.join_on() != null) collectTableSourceItems(suffix.join_on().table_source(), items);
            else if (suffix.cross_join() != null) collectTableSourceItems(suffix.cross_join().table_source(), items);
            else if (suffix.apply_() != null) collectTableSourceItems(suffix.apply_().table_source(), items);
        }
    }

    protected String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    protected String joinKind(SqlServerRelationSqlParser.Join_typeContext joinType) {
        if (joinType == null) return "JOIN";
        if (joinType.LEFT() != null) return "LEFT_JOIN";
        if (joinType.RIGHT() != null) return "RIGHT_JOIN";
        if (joinType.FULL() != null) return "FULL_JOIN";
        return "JOIN";
    }

    protected List<String> identifiers(SqlServerRelationSqlParser.Column_name_listContext ctx) {
        return ctx.id_().stream().map(id -> clean(id.getText())).toList();
    }

    protected List<String> identifiers(SqlServerRelationSqlParser.Column_name_list_with_orderContext ctx) {
        return ctx.id_().stream().map(id -> clean(id.getText())).toList();
    }

    protected String qualifiedName(SqlServerRelationSqlParser.Full_table_nameContext ctx) {
        return String.join(".", ctx.id_().stream().map(id -> clean(id.getText())).toList());
    }

    protected List<String> parts(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '.') {
                String part = clean(current.toString());
                if (!part.isBlank()) result.add(part);
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        String part = clean(current.toString());
        if (!part.isBlank()) result.add(part);
        return List.copyOf(result);
    }

    protected String lastPart(String raw) {
        List<String> nameParts = parts(raw);
        return nameParts.isEmpty() ? "" : nameParts.get(nameParts.size() - 1);
    }

    protected String clean(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        while ((text.startsWith("[") && text.endsWith("]"))
                || (text.startsWith("\"") && text.endsWith("\""))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    protected String baseName(String qualified) {
        if (qualified == null) return "";
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? clean(qualified) : clean(qualified.substring(dot + 1));
    }

    protected boolean isTemp(String table) { return clean(table).startsWith("#"); }

    protected abstract ColumnRead singleColumn(SqlServerRelationSqlParser.ExpressionContext expression);

    protected record ColumnRead(String alias, String column) { }

    protected record ExpressionAnalysis(
            List<ColumnRead> sources,
            LineageTransformType transform,
            LineageFlowKind flowKind,
            List<ColumnRead> controlSources,
            LineageTransformType controlTransform) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE,
                    List.of(), LineageTransformType.DIRECT);
        }
        static ExpressionAnalysis of(ColumnRead column, LineageTransformType transform,
                LineageFlowKind flowKind) {
            return new ExpressionAnalysis(List.of(column), transform, flowKind,
                    List.of(), LineageTransformType.DIRECT);
        }
        static ExpressionAnalysis combine(LineageTransformType transform, LineageFlowKind flowKind,
                ExpressionAnalysis left, ExpressionAnalysis right) {
            List<ColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            List<ColumnRead> controls = new ArrayList<>();
            controls.addAll(left.controlSources());
            controls.addAll(right.controlSources());
            return new ExpressionAnalysis(sources.stream().distinct().toList(),
                    LineageTransformClassifier.dominantForFlow(
                            flowKind, transform, left.transform(), right.transform()), flowKind,
                    controls.stream().distinct().toList(),
                    LineageTransformClassifier.dominant(left.controlTransform(), right.controlTransform()));
        }
        List<String> aliases() { return sources.stream().map(ColumnRead::alias).toList(); }
        List<String> columns() { return sources.stream().map(ColumnRead::column).toList(); }
        List<String> controlAliases() { return controlSources.stream().map(ColumnRead::alias).toList(); }
        List<String> controlColumns() { return controlSources.stream().map(ColumnRead::column).toList(); }
        boolean hasSources() { return !sources.isEmpty() || !controlSources.isEmpty(); }
    }
}
