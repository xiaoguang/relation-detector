package com.relationdetector.core.fullgrammer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RowsetScopeSink {
    private final SourceLocationSupport source;
    private final ArrayDeque<String> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<Integer> projectionRowsetBases = new ArrayDeque<>();
    private final ArrayDeque<Integer> selectRowsetBases = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargets = new ArrayDeque<>();
    private final Set<String> ignoredNames = new LinkedHashSet<>();
    private final Set<String> functionRowsetNames = new LinkedHashSet<>();
    private final Map<String, String> aliasToTable = new LinkedHashMap<>();
    private final List<RowsetRegistration> rowsetRegistrations = new ArrayList<>();
    private final List<String> rowsetTables = new ArrayList<>();

    RowsetScopeSink(SourceLocationSupport source) {
        this.source = source;
    }

    void withProjectionOwner(String owner, Runnable visitor) {
        if (owner == null || owner.isBlank()) {
            visitor.run();
            return;
        }
        int rowsetMark = rowsetTables.size();
        projectionOwners.push(source.clean(owner));
        projectionRowsetBases.push(rowsetMark);
        try {
            visitor.run();
        } finally {
            restoreRowsetScope(rowsetMark);
            projectionRowsetBases.pop();
            projectionOwners.pop();
        }
    }

    void withWriteTarget(String tableOrAlias, Runnable visitor) {
        if (tableOrAlias == null || tableOrAlias.isBlank()) {
            visitor.run();
            return;
        }
        writeTargets.push(source.clean(tableOrAlias));
        try {
            visitor.run();
        } finally {
            writeTargets.pop();
        }
    }

    void withSelectScope(Runnable visitor) {
        int rowsetMark = rowsetTables.size();
        selectRowsetBases.push(rowsetMark);
        try {
            visitor.run();
        } finally {
            restoreRowsetScope(rowsetMark);
            selectRowsetBases.pop();
        }
    }

    String currentProjectionOwner() {
        return projectionOwners.isEmpty() ? "" : projectionOwners.peek();
    }

    String currentWriteTarget() {
        return writeTargets.isEmpty() ? "" : writeTargets.peek();
    }

    int rowsetScopeMark() {
        return rowsetTables.size();
    }

    void restoreRowsetScope(int mark) {
        if (mark < 0 || mark > rowsetTables.size()) {
            return;
        }
        while (rowsetTables.size() > mark) {
            rowsetTables.remove(rowsetTables.size() - 1);
            rowsetRegistrations.remove(rowsetRegistrations.size() - 1);
        }
        rebuildAliasIndex();
    }

    void registerRowset(String qualifiedTable, String alias) {
        String table = source.baseName(qualifiedTable);
        if (table.isBlank()) {
            return;
        }
        aliasToTable.put(table.toLowerCase(Locale.ROOT), table);
        String cleanAlias = source.clean(alias);
        rowsetTables.add(cleanAlias.isBlank() ? table : cleanAlias);
        rowsetRegistrations.add(new RowsetRegistration(table, cleanAlias));
        if (!cleanAlias.isBlank()) {
            aliasToTable.put(cleanAlias.toLowerCase(Locale.ROOT), table);
        }
    }

    void markIgnoredRowset(String name, String reason) {
        String cleanName = source.clean(name);
        if (cleanName.isBlank()) {
            return;
        }
        ignoredNames.add(cleanName.toLowerCase(Locale.ROOT));
        ignoredNames.add(source.baseName(cleanName).toLowerCase(Locale.ROOT));
        if ("FUNCTION_ROWSET".equals(reason)) {
            functionRowsetNames.add(cleanName.toLowerCase(Locale.ROOT));
            functionRowsetNames.add(source.baseName(cleanName).toLowerCase(Locale.ROOT));
        }
    }

    String defaultProjectionQualifier() {
        Integer base = selectRowsetBases.peek();
        if (base == null) {
            base = projectionRowsetBases.peek();
        }
        if (base == null) {
            return "";
        }
        List<String> physicalRowsets = rowsetTables.subList(base, rowsetTables.size()).stream()
                .filter(rowset -> !functionRowsetNames.contains(source.clean(rowset).toLowerCase(Locale.ROOT)))
                .toList();
        if (physicalRowsets.size() == 1) {
            return physicalRowsets.get(0);
        }
        return "";
    }

    String tableFor(String aliasOrTable) {
        String clean = source.clean(aliasOrTable);
        if (clean.isBlank()) {
            return "";
        }
        return aliasToTable.getOrDefault(clean.toLowerCase(Locale.ROOT), clean);
    }

    private void rebuildAliasIndex() {
        aliasToTable.clear();
        for (RowsetRegistration registration : rowsetRegistrations) {
            aliasToTable.put(registration.table().toLowerCase(Locale.ROOT), registration.table());
            if (!registration.alias().isBlank()) {
                aliasToTable.put(registration.alias().toLowerCase(Locale.ROOT), registration.table());
            }
        }
    }

    private record RowsetRegistration(String table, String alias) {
    }
}
