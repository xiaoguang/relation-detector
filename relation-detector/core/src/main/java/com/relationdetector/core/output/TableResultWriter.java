package com.relationdetector.core.output;

import java.util.stream.Collectors;

import com.relationdetector.core.scan.ScanResult;

/**
 *
 * Human-readable table writer for terminal use.
 */
public final class TableResultWriter {
    public String write(ScanResult result) {
        if (result.relationships().isEmpty()) {
            return "No relationships detected.\nWarnings: " + result.warnings().size() + "\n";
        }
        StringBuilder out = new StringBuilder();
        out.append(String.format("%-28s %-28s %-15s %-24s %-6s %s%n",
                "SOURCE", "TARGET", "TYPE", "SUBTYPE", "CONF", "EVIDENCE"));
        for (var relation : result.relationships()) {
            String evidence = relation.evidence().stream()
                    .map(item -> item.type().name())
                    .distinct()
                    .collect(Collectors.joining(","));
            out.append(String.format("%-28s %-28s %-15s %-24s %-6s %s%n",
                    relation.source().displayName(),
                    relation.target().displayName(),
                    relation.relationType(),
                    relation.relationSubType(),
                    relation.confidence(),
                    evidence));
        }
        out.append("\nWarnings: ").append(result.warnings().size()).append("\n");
        for (var warning : result.warnings()) {
            out.append("- ").append(warning.source()).append(":").append(warning.line())
                    .append(" ").append(warning.message()).append("\n");
        }
        return out.toString();
    }
}
