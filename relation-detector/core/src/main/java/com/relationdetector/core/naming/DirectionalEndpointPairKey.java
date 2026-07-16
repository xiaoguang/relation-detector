package com.relationdetector.core.naming;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

record DirectionalEndpointPairKey(String sourceKey, String targetKey) {
    static DirectionalEndpointPairKey of(
            RelationshipCandidate candidate,
            CanonicalEndpointKeyProvider endpointKeys
    ) {
        return new DirectionalEndpointPairKey(
                endpointKeys.factKey(candidate.source()),
                endpointKeys.factKey(candidate.target()));
    }
}
