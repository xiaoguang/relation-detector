package com.relationdetector.core.output;

import java.util.stream.Collectors;

import com.relationdetector.core.scan.ScanResult;

/**
 * CN: 将已合并 ScanResult 渲染为终端可读表格，不改变候选、证据或 confidence。
 * EN: Renders a merged ScanResult as a terminal-readable table without changing candidates, evidence, or confidence.
 */
public final class TableResultWriter {
    public String write(ScanResult result) {
        StringBuilder out = new StringBuilder();
        if (result.relationships().isEmpty()) {
            out.append("No relationships detected.\n");
        } else {
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
        }
        out.append("\nWarnings: ").append(result.warnings().size()).append("\n");
        for (var warning : result.warnings()) {
            out.append("- ").append(warning.source()).append(":").append(warning.line())
                    .append(" ").append(warning.message()).append("\n");
        }
        return out.toString();
    }
}
