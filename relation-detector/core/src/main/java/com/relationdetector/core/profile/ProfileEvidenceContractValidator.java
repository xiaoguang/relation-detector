package com.relationdetector.core.profile;

import java.util.List;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.spi.ProfileStatus;

/**
 * CN: 在 core 消费 adaptor 画像结果前验证状态、证据类型和负向策略，不负责执行 JDBC 查询或修改候选。
 *
 * <p>EN: Validates profile outcome status, evidence types, and negative-evidence policy before core consumes an
 * adaptor result. It neither executes JDBC queries nor mutates relationship candidates.
 */
public final class ProfileEvidenceContractValidator {
    private static final Set<EvidenceType> ALLOWED_TYPES = Set.of(
            EvidenceType.VALUE_CONTAINMENT_HIGH,
            EvidenceType.VALUE_OVERLAP_HIGH,
            EvidenceType.NEGATIVE_VALUE_MISMATCH);
    private final NegativeProfileEvidencePolicy negativePolicy = new NegativeProfileEvidencePolicy();

    public List<Evidence> validate(ProfileRequest request, ProfileOutcome outcome) {
        if (outcome == null) {
            throw violation();
        }
        List<Evidence> evidence = outcome.evidence();
        if (outcome.status() != ProfileStatus.SUCCESS && !evidence.isEmpty()) {
            throw violation();
        }
        for (Evidence item : evidence) {
            if (!ALLOWED_TYPES.contains(item.type()) || item.sourceType() != EvidenceSourceType.DATA_PROFILE) {
                throw violation();
            }
            if (item.type() == EvidenceType.NEGATIVE_VALUE_MISMATCH
                    && (!negativePolicy.allows(request)
                    || !"LIVE_DATABASE".equals(item.attributes().get("profileMode"))
                    || !"DECLARED_FOREIGN_KEY_ONLY".equals(item.attributes().get("negativePolicy")))) {
                throw violation();
            }
        }
        return evidence;
    }

    private IllegalStateException violation() {
        return new IllegalStateException("Data profiler violated the ProfileOutcome evidence contract");
    }
}
