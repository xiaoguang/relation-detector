package com.relationdetector.core.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.core.execution.StatementExecutionOutcome;

/**
 * Task-local sensitivity policy for parser diagnostics produced from live database text.
 * Inputs have already passed their SPI result validators; this policy replaces every
 * diagnostic payload with core-owned identity before a task result can be merged.
 */
final class LiveParserDiagnosticPolicy {
    private final LiveDiagnosticSanitizer.Operation operation;
    private final String source;
    private final Map<String, Object> identity;

    private LiveParserDiagnosticPolicy(
            LiveDiagnosticSanitizer.Operation operation,
            String source,
            Map<String, Object> identity
    ) {
        this.operation = operation;
        this.source = source;
        this.identity = Map.copyOf(identity);
    }

    static LiveParserDiagnosticPolicy object(String source, Map<String, Object> identity) {
        return new LiveParserDiagnosticPolicy(
                LiveDiagnosticSanitizer.Operation.OBJECT, source, identity);
    }

    static LiveParserDiagnosticPolicy databaseDdl(String source, Map<String, Object> identity) {
        return new LiveParserDiagnosticPolicy(
                LiveDiagnosticSanitizer.Operation.DATABASE_DDL, source, identity);
    }

    StatementExecutionOutcome sanitizeOutcome(StatementExecutionOutcome outcome) {
        outcome.relationshipCandidates().forEach(candidate ->
                replace(candidate.warnings(), sanitizeWarnings(candidate.warnings())));
        outcome.lineageCandidates().forEach(candidate ->
                replace(candidate.warnings(), sanitizeWarnings(candidate.warnings())));
        return new StatementExecutionOutcome(
                outcome.relationshipCandidates(),
                outcome.lineageCandidates(),
                outcome.namingEvidence(),
                sanitizeWarnings(outcome.warnings()),
                outcome.ddlEvidenceInventory(),
                outcome.ddlCatalogInventory());
    }

    List<WarningMessage> sanitizeWarnings(List<WarningMessage> warnings) {
        return warnings.stream().map(this::sanitize).toList();
    }

    WarningMessage sanitizeFailure(WarningMessage warning, Throwable failure) {
        return LiveDiagnosticSanitizer.liveParserFailure(
                warning.type(), warning.code(), operation, source, identity, failure);
    }

    private WarningMessage sanitize(WarningMessage warning) {
        return LiveDiagnosticSanitizer.liveParserDiagnostic(
                warning.type(), warning.code(), operation, source, identity);
    }

    private void replace(List<WarningMessage> target, List<WarningMessage> sanitized) {
        List<WarningMessage> detached = new ArrayList<>(sanitized);
        target.clear();
        target.addAll(detached);
    }
}
