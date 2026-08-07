package com.relationdetector.core.profile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;
import com.relationdetector.core.diagnostics.LiveDiagnosticSanitizer;
import com.relationdetector.core.adaptor.AdaptorContractException;
import com.relationdetector.core.adaptor.AdaptorResultDetachmentSupport;

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
    private final DataProfileEvidenceBuilder evidenceBuilder = new DataProfileEvidenceBuilder();

    /**
     * CN: 在调用外部 profiler 前，从 core-owned candidate 捕获负向策略与诊断端点；返回值不引用可变
     * candidate，插件随后修改 request 不会改变资格。
     * EN: Captures negative-evidence policy and diagnostic endpoints from the core-owned candidate before the
     * external profiler runs. The snapshot retains no mutable candidate reference.
     */
    public ProfileValidationContext capture(ProfileRequest request) {
        if (request == null) {
            throw violation();
        }
        RelationshipCandidate candidate = request.candidate();
        ProfileRequest trusted = new ProfileRequest(
                detachment.relationshipCandidate(candidate, "data profile validation candidate"),
                request.options());
        return new ProfileValidationContext(
                trusted,
                new NegativeProfileEligibility(
                        negativePolicy.allows(trusted.candidate()),
                        trusted.candidate().source().normalizedKey(),
                        trusted.candidate().target().normalizedKey()));
    }

    public ValidatedProfileOutcome validate(
            ProfileValidationContext context,
            ProfileOutcome outcome,
            String adaptorId
    ) {
        if (context == null || outcome == null) {
            throw violation();
        }
        List<Evidence> evidence = outcome.evidence().stream()
                .map(item -> detachment.evidence(item, "data profile outcome evidence"))
                .toList();
        validateStatusShape(outcome.status(), evidence, outcome.warnings());
        List<Evidence> canonicalEvidence = outcome.status() == ProfileStatus.SUCCESS
                ? validateAndRebuildEvidence(context, evidence, adaptorId)
                : List.of();
        return new ValidatedProfileOutcome(
                canonicalEvidence,
                rebuiltWarnings(context.negativeEligibility(), outcome, adaptorId));
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

    private List<Evidence> validateAndRebuildEvidence(
            ProfileValidationContext context,
            List<Evidence> evidence,
            String adaptorId
    ) {
        Set<EvidenceType> types = new java.util.LinkedHashSet<>();
        for (Evidence item : evidence) {
            if (item == null || !ALLOWED_TYPES.contains(item.type())
                    || item.sourceType() != EvidenceSourceType.DATA_PROFILE
                    || !types.add(item.type())) {
                throw violation();
            }
        }
        DataProfileMetrics metrics = metrics(evidence.get(0).attributes());
        String source = safeAdaptorId(adaptorId) + "-data-profile";
        List<Evidence> canonical = evidenceBuilder.build(context.request(), metrics, source);
        if (canonical.size() != evidence.size()) {
            throw violation();
        }
        Map<EvidenceType, Evidence> expected = new java.util.EnumMap<>(EvidenceType.class);
        canonical.forEach(item -> expected.put(item.type(), item));
        for (Evidence item : evidence) {
            Evidence canonicalItem = expected.get(item.type());
            if (canonicalItem == null || !canonicalItem.attributes().equals(item.attributes())) {
                throw violation();
            }
        }
        return canonical;
    }

    private DataProfileMetrics metrics(Map<String, Object> attributes) {
        if (attributes == null || !"LIVE_DATABASE".equals(attributes.get("profileMode"))) {
            throw violation();
        }
        long sourceRows = count(attributes, "sourceNonNullRows");
        long sourceDistinct = count(attributes, "sourceDistinctValues");
        long matched = count(attributes, "matchedDistinctSourceValues");
        long missing = count(attributes, "missingDistinctSourceValues");
        long targetDistinct = count(attributes, "targetDistinctValues");
        boolean timedOut = bool(attributes, "queryTimedOut");
        boolean permissionDenied = bool(attributes, "permissionDenied");
        if (timedOut || permissionDenied
                || matched > sourceDistinct
                || sourceDistinct > sourceRows
                || matched > targetDistinct) {
            throw violation();
        }
        try {
            if (Math.subtractExact(sourceDistinct, matched) != missing) {
                throw violation();
            }
        } catch (ArithmeticException failure) {
            throw violation();
        }
        return new DataProfileMetrics(
                "LIVE_DATABASE", sourceRows, sourceDistinct, matched, missing,
                targetDistinct, false, false);
    }

    private long count(Map<String, Object> attributes, String field) {
        Object value = attributes.get(field);
        if (!(value instanceof Long count) || count < 0) {
            throw violation();
        }
        return count;
    }

    private boolean bool(Map<String, Object> attributes, String field) {
        Object value = attributes.get(field);
        if (!(value instanceof Boolean result)) {
            throw violation();
        }
        return result;
    }

    private List<WarningMessage> rebuiltWarnings(
            NegativeProfileEligibility eligibility,
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
                        "sourceEndpoint", eligibility.sourceEndpoint(),
                        "targetEndpoint", eligibility.targetEndpoint(),
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
     * CN: 保存插件调用前确定的负向 evidence 资格和安全端点文本；不携带 candidate 或其他可变 scan 状态。
     * EN: Stores pre-plugin negative-evidence eligibility and safe endpoint text without retaining candidate or
     * other mutable scan state.
     */
    public record NegativeProfileEligibility(
            boolean negativeAllowed,
            String sourceEndpoint,
            String targetEndpoint
    ) {
        public NegativeProfileEligibility {
            if (sourceEndpoint == null || sourceEndpoint.isBlank()
                    || targetEndpoint == null || targetEndpoint.isBlank()) {
                throw new AdaptorContractException("Data profiler violated the ProfileOutcome contract");
            }
        }
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
