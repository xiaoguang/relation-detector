package com.relationdetector.core.naming;

import com.relationdetector.contracts.model.RelationshipCandidate;

record DirectionalEndpointPairKey(String sourceKey, String targetKey) {
    static DirectionalEndpointPairKey of(RelationshipCandidate candidate) {
        return new DirectionalEndpointPairKey(
                candidate.source().normalizedKey(),
                candidate.target().normalizedKey());
    }
}
