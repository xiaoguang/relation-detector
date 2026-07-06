package com.relationdetector.core.scan;

import java.sql.Connection;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.spi.ProfileRequest;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.core.profile.DataProfileCandidateGenerator;

final class DataProfilePipeline {
    private final DataProfileCandidateGenerator candidateGenerator = new DataProfileCandidateGenerator();

    void profile(Connection connection, ScanPipelineContext ctx) {
        if (connection == null || !ctx.config.dataProfileEnabled) {
            return;
        }
        ctx.adaptor.profiling().dataProfiler().ifPresent(profiler -> {
            ctx.result.sources().add("data-profile");
            List<RelationshipCandidate> selected = candidateGenerator.select(
                    ctx.relationshipCandidates,
                    ctx.metadataSnapshot,
                    ctx.namingEvidencePool.merged(),
                    ctx.config.dataProfileOptions());
            for (RelationshipCandidate candidate : selected) {
                boolean existingCandidate = ctx.relationshipCandidates.contains(candidate);
                List<Evidence> evidence = profiler.profile(connection,
                        new ProfileRequest(candidate, ctx.config.dataProfileOptions()));
                if (evidence.isEmpty()) {
                    continue;
                }
                if (!existingCandidate && !profileCanCreateRelationship(candidate, evidence)) {
                    continue;
                }
                candidate.evidence().addAll(evidence);
                if (!existingCandidate) {
                    ctx.relationshipCandidates.add(candidate);
                }
            }
        });
    }

    private boolean profileCanCreateRelationship(RelationshipCandidate candidate, List<Evidence> evidence) {
        return candidate.relationSubType() == RelationSubType.PROFILE_SUPPORTED_FK
                && evidence.stream().anyMatch(item -> item.type() == EvidenceType.VALUE_CONTAINMENT_HIGH);
    }
}
