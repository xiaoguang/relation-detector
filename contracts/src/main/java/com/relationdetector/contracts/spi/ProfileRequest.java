package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.model.RelationshipCandidate;

/** Request to profile one candidate relation. */
public record ProfileRequest(
        RelationshipCandidate candidate,
        int sampleRows,
        int timeoutSeconds
) {
}
