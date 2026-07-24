package com.relationdetector.core.profile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.core.scan.AdaptorContractException;
import com.relationdetector.core.scan.AdaptorResultDetachmentSupport;

/**
 * CN: 在 core 消费 adaptor 画像结果前原子验证 status、evidence 和 warning envelope，并重建安全 warning；
 * 输入是单个 SPI outcome，输出只含可信 evidence 与 core 生成的 diagnostics。本类不执行 JDBC、不修改
 * candidate，也不信任插件提供的 message、source 或 attributes。
 *
 * <p>EN: Atomically validates an adaptor profile outcome's status, evidence, and warning envelope before core
 * consumption, then rebuilds safe warnings. It produces only trusted evidence and core-generated diagnostics;
 * it neither executes JDBC nor mutates candidates, and never trusts plugin messages, sources, or attributes.
 */
public final class ProfileOutcomeContractValidator {
    private static final Set<EvidenceType> ALLOWED_TYPES = Set.of(
            EvidenceType.VALUE_CONTAINMENT_HIGH,
            EvidenceType.VALUE_OVERLAP_HIGH,
            EvidenceType.NEGATIVE_VALUE_MISMATCH);
    private final NegativeProfileEvidencePolicy negativePolicy = new NegativeProfileEvidencePolicy();
    private final AdaptorResultDetachmentSupport detachment = new AdaptorResultDetachmentSupport();

    public ValidatedProfileOutcome validate(ProfileRequest request, ProfileOutcome outcome, String adaptorId) {
        if (request == null || outcome == null) {
            throw violation();
        }
        List<Evidence> evidence = outcome.evidence().stream()
                .map(item -> detachment.evidence(item, "data profile outcome evidence"))
                .toList();
        validateStatusShape(outcome.status(), evidence, outcome.warnings());
        validateEvidence(request, evidence);
        return new ValidatedProfileOutcome(evidence, rebuiltWarnings(request, outcome, adaptorId));
    }

    private void validateStatusShape(
            ProfileStatus status,
            List<Evidence> evidence,
            List<WarningMessage> warnings
    ) {
        switch (status) {
            case SUCCESS -> {
                if (evidence.isEmpty() || !warnings.isEmpty()) throw violation();
            }
            case NO_EVIDENCE, SKIPPED_INVALID_ENDPOINT -> {
                if (!evidence.isEmpty() || !warnings.isEmpty()) throw violation();
            }
            case PERMISSION_DENIED, TIMEOUT, QUERY_FAILED -> {
                if (!evidence.isEmpty() || warnings.size() > 1) throw violation();
                if (!warnings.isEmpty()) validateFailureWarning(status, warnings.get(0));
            }
        }
    }

    private void validateFailureWarning(ProfileStatus status, WarningMessage warning) {
        if (warning == null
                || warning.type() != WarningType.PROFILE_WARNING
                || !expectedCode(status).equals(warning.code())) {
            throw violation();
        }
    }

    private void validateEvidence(ProfileRequest request, List<Evidence> evidence) {
        for (Evidence item : evidence) {
            if (item == null || !ALLOWED_TYPES.contains(item.type())
                    || item.sourceType() != EvidenceSourceType.DATA_PROFILE) {
                throw violation();
            }
            if (item.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH
                    && (!negativePolicy.allows(request)
                    || !"LIVE_DATABASE".equals(item.attributes().get("profileMode"))
                    || !"DECLARED_FOREIGN_KEY_ONLY".equals(item.attributes().get("negativePolicy")))) {
                throw violation();
            }
        }
    }

    private List<WarningMessage> rebuiltWarnings(
            ProfileRequest request,
            ProfileOutcome outcome,
            String adaptorId
    ) {
        if (outcome.status() == ProfileStatus.SUCCESS
                || outcome.status() == ProfileStatus.NO_EVIDENCE
                || outcome.status() == ProfileStatus.SKIPPED_INVALID_ENDPOINT) {
            return List.of();
        }
        LiveDiagnosticSanitizer.Operation operation = switch (outcome.status()) {
            case PERMISSION_DENIED -> LiveDiagnosticSanitizer.Operation.PROFILE_PERMISSION;
            case TIMEOUT -> LiveDiagnosticSanitizer.Operation.PROFILE_TIMEOUT;
            default -> LiveDiagnosticSanitizer.Operation.PROFILE_QUERY;
        };
        String profilerSource = safeAdaptorId(adaptorId);
        WarningMessage warning = LiveDiagnosticSanitizer.warning(
                WarningType.PROFILE_WARNING,
                expectedCode(outcome.status()),
                operation,
                "data-profile:" + profilerSource,
                null,
                Map.of(
                        "sourceEndpoint", request.candidate().source().normalizedKey(),
                        "targetEndpoint", request.candidate().target().normalizedKey(),
                        "profilerSource", profilerSource));
        return List.of(warning);
    }

    private String expectedCode(ProfileStatus status) {
        return switch (status) {
            case PERMISSION_DENIED -> "PROFILE_PERMISSION_DENIED";
            case TIMEOUT -> "PROFILE_QUERY_TIMEOUT";
            case QUERY_FAILED -> "PROFILE_QUERY_FAILED";
            default -> "";
        };
    }

    private String safeAdaptorId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        boolean safe = value.codePoints().allMatch(character -> Character.isLetterOrDigit(character)
                || character == '-' || character == '_' || character == '.');
        return safe ? value : "unknown";
    }

    private AdaptorContractException violation() {
        return new AdaptorContractException("Data profiler violated the ProfileOutcome contract");
    }

    /**
     * CN: 保存通过完整 SPI 契约校验的 evidence 和 core 重建 warnings，供 scan pipeline 延迟应用。
     * EN: Carries fully validated evidence and core-rebuilt warnings for deferred scan-pipeline application.
     */
    public record ValidatedProfileOutcome(List<Evidence> evidence, List<WarningMessage> warnings) {
        public ValidatedProfileOutcome {
            evidence = List.copyOf(evidence);
            warnings = List.copyOf(warnings);
        }
    }
}
