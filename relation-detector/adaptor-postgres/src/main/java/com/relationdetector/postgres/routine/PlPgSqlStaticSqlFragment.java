package com.relationdetector.postgres.routine;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Builds an embedded SQL fragment while preserving its original line layout. */
public final class PlPgSqlStaticSqlFragment {
    private PlPgSqlStaticSqlFragment() {
    }

    public static PlPgSqlBodyStructure.StaticSqlStatement from(
            String source,
            ParserRuleContext statement,
            List<MaskRange> masks
    ) {
        int startLine = statement.getStart().getLine();
        int endLine = statement.getStop() == null ? startLine : statement.getStop().getLine();
        int start = Math.max(0, statement.getStart().getStartIndex());
        int stop = statement.getStop() == null ? start - 1 : statement.getStop().getStopIndex();
        if (stop < start || start >= source.length()) {
            return new PlPgSqlBodyStructure.StaticSqlStatement("", startLine, endLine);
        }
        char[] sql = source.substring(start, Math.min(source.length(), stop + 1)).toCharArray();
        for (MaskRange mask : masks) {
            int localStart = Math.max(0, mask.startIndex() - start);
            int localStop = Math.min(sql.length - 1, mask.stopIndex() - start);
            for (int index = localStart; index <= localStop; index++) {
                if (sql[index] != '\n' && sql[index] != '\r') {
                    sql[index] = ' ';
                }
            }
        }
        return new PlPgSqlBodyStructure.StaticSqlStatement(new String(sql), startLine, endLine);
    }

    public static MaskRange range(ParserRuleContext context) {
        return range(context.getStart(), context.getStop());
    }

    public static MaskRange range(Token start, Token stop) {
        int startIndex = start == null ? 0 : Math.max(0, start.getStartIndex());
        int stopIndex = stop == null ? startIndex - 1 : Math.max(startIndex - 1, stop.getStopIndex());
        return new MaskRange(startIndex, stopIndex);
    }

    public record MaskRange(int startIndex, int stopIndex) {
    }
}
