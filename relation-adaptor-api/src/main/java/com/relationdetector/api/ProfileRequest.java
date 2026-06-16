package com.relationdetector.api;

/** Request to profile one candidate relation. */
public record ProfileRequest(
        RelationshipCandidate candidate,
        int sampleRows,
        int timeoutSeconds
) {
}
