package com.relationdetector.core.provenance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * CN: 验证所有 parser frontend 共用的 absolute source-line 契约，阻止越界行号和绝对路径进入 evidence。
 * EN: Validates the absolute source-line contract shared by all parser frontends and rejects out-of-range lines or absolute paths.
 */
public final class SourceProvenanceValidator {
    public List<WarningMessage> validate(SqlStatementRecord statement, StructuredParseResult result) {
        long sourceFileLineCount = number(statement, "sourceFileLineCount");
        List<WarningMessage> warnings = new ArrayList<>();
        result.events().forEach(event -> validate(statement, event.provenance(), sourceFileLineCount)
                .forEach(violation -> warnings.add(WarningMessage.warn(
                        WarningType.PARSE_WARNING,
                        violation.code(),
                        violation.message(),
                        statement.sourceName(),
                        event.line()))));
        return List.copyOf(warnings);
    }

    public List<Violation> validate(
            SqlStatementRecord statement,
            SourceProvenance provenance,
            long sourceFileLineCount
    ) {
        List<Violation> violations = new ArrayList<>();
        long line = provenance.line();
        if (line < statement.startLine() || line > statement.endLine()) {
            violations.add(new Violation("SOURCE_LINE_OUTSIDE_STATEMENT",
                    "sourceLine " + line + " is outside "
                            + statement.startLine() + "-" + statement.endLine()));
        }
        if (sourceFileLineCount > 0 && line > sourceFileLineCount) {
            violations.add(new Violation("SOURCE_LINE_OUTSIDE_FILE",
                    "sourceLine " + line + " exceeds file line count " + sourceFileLineCount));
        }
        String sourceFile = firstNonBlank(provenance.sourceFile(), text(statement, "sourceFile"));
        if (isAbsolute(sourceFile)) {
            violations.add(new Violation("ABSOLUTE_SOURCE_FILE",
                    "sourceFile must be repository-relative: " + sourceFile));
        }
        return List.copyOf(violations);
    }

    private String text(SqlStatementRecord statement, String key) {
        Object value = statement.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long number(SqlStatementRecord statement, String key) {
        Object value = statement.attributes().get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private boolean isAbsolute(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            if (Path.of(value).isAbsolute()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Preserve the validation result for malformed foreign-platform paths.
        }
        return value.length() > 2 && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '/' || value.charAt(2) == '\\');
    }

    public record Violation(String code, String message) {
    }
}
