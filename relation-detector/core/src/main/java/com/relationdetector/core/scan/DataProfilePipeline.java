package com.relationdetector.core.scan;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ProfileOutcome;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.core.profile.DataProfileCandidateGenerator;
import com.relationdetector.core.profile.ProfileEvidenceContractValidator;
import com.relationdetector.core.identity.NamespaceContext;

/**
 * CN: 选择有界 live profiling candidates，调用 adaptor profiler 并在增强前原子验证 evidence 契约。
 * EN: Selects bounded live-profiling candidates, invokes the adaptor profiler, and atomically validates its evidence contract before enhancement.
 */
final class DataProfilePipeline {
    private final DataProfileCandidateGenerator candidateGenerator = new DataProfileCandidateGenerator();
    private final DataProfileNamespacePolicy namespacePolicy = new DataProfileNamespacePolicy();
    private final ProfileEvidenceContractValidator evidenceContract = new ProfileEvidenceContractValidator();

    List<RelationshipCandidate> profile(Connection connection, ScanPipelineContext ctx) {
        EvidenceConfig evidenceConfig = ctx.config.evidence();
        if (connection == null || !evidenceConfig.dataProfileEnabled()) {
            return List.of();
        }
        List<RelationshipCandidate> added = new java.util.ArrayList<>();
        ctx.adaptor.profiling().dataProfiler().ifPresent(profiler -> {
            ctx.result.sources().add("data-profile");
            List<RelationshipCandidate> selected = candidateGenerator.select(
                    ctx.relationshipCandidates,
                    ctx.metadataSnapshot,
                    ctx.namingEvidencePool.merged(),
                    evidenceConfig.dataProfileOptions(),
                    ctx.adaptor.identifierRules(),
                    new NamespaceContext(ctx.scope.catalog(), ctx.scope.schema(), List.of()));
            for (RelationshipCandidate candidate : selected) {
                if (!namespacePolicy.supports(ctx.config.database().databaseType(), ctx.scope,
                        ctx.adaptor.identifierRules(), candidate)) {
                    continue;
                }
                boolean existingCandidate = ctx.relationshipCandidates.contains(candidate);
                ProfileRequest request = new ProfileRequest(candidate, evidenceConfig.dataProfileOptions());
                ProfileOutcome outcome = profiler.profile(connection, request);
                List<Evidence> evidence = evidenceContract.validate(request, outcome);
                ctx.result.warnings().addAll(outcome.warnings());
                if (evidence.isEmpty()) {
                    continue;
                }
                if (!existingCandidate && !profileCanCreateRelationship(candidate, evidence)) {
                    continue;
                }
                candidate.evidence().addAll(evidence);
                if (!existingCandidate) {
                    ctx.relationshipCandidates.add(candidate);
                    added.add(candidate);
                }
            }
        });
        return List.copyOf(added);
    }

    private boolean profileCanCreateRelationship(RelationshipCandidate candidate, List<Evidence> evidence) {
        return candidate.relationSubType() == RelationSubType.PROFILE_SUPPORTED_FK
                && evidence.stream().anyMatch(item -> item.type() == EvidenceType.VALUE_CONTAINMENT_HIGH);
    }
}
