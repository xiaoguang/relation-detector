package com.relationdetector.core.naming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.relationdetector.contracts.model.NamingEvidenceCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;

/**
 *
 * Scan-scoped canonical naming evidence pool.
 *
 * <p>CN: namingEvidence 的唯一 scan 级证据池。调用方可以从 metadata、DDL
 * inventory 或 SQL predicate 持续加入 observation；本类按稳定 id 合并，并为
 * relationship enhancer 提供 indexed lookup。</p>
 */
public final class NamingEvidencePool {
    private final CanonicalEndpointKeyProvider endpointKeys;
    private final NamingEvidenceMerger merger;
    private final Map<String, NamingEvidenceCandidate> byKey = new LinkedHashMap<>();

    public NamingEvidencePool() {
        this(CanonicalEndpointKeyProvider.defaults());
    }

    public NamingEvidencePool(CanonicalEndpointKeyProvider endpointKeys) {
        this.endpointKeys = java.util.Objects.requireNonNull(endpointKeys, "endpointKeys");
        this.merger = new NamingEvidenceMerger(endpointKeys);
    }

    public void add(NamingEvidenceCandidate candidate) {
        if (candidate == null) {
            return;
        }
        String key = key(candidate);
        byKey.put(key, mergeOne(byKey.get(key), candidate));
    }

    public void addAll(Collection<NamingEvidenceCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        candidates.forEach(this::add);
    }

    public List<NamingEvidenceCandidate> merged() {
        return byKey.values().stream()
                .sorted(Comparator
                        .comparing((NamingEvidenceCandidate candidate) -> candidate.source().displayName())
                        .thenComparing(candidate -> candidate.target().displayName())
                        .thenComparing(NamingEvidenceCandidate::rule))
                .toList();
    }

    public Optional<NamingEvidenceCandidate> findFor(RelationshipCandidate candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        return byKey.values().stream()
                .filter(item -> sameEndpointPair(item, candidate))
                .findFirst();
    }

    /** Replaces each retained raw naming observation through the scan-local adjustment hook. */
    public void adjustRawEvidence(java.util.function.UnaryOperator<com.relationdetector.contracts.model.Evidence> adjuster) {
        java.util.Objects.requireNonNull(adjuster, "adjuster");
        List<NamingEvidenceCandidate> adjusted = byKey.values().stream()
                .map(candidate -> new NamingEvidenceCandidate(
                        candidate.source(), candidate.target(), candidate.evidence(), candidate.rule(),
                        candidate.directionHint(), candidate.rawEvidence().stream().map(adjuster).toList()))
                .toList();
        byKey.clear();
        adjusted.forEach(this::add);
    }

    private NamingEvidenceCandidate mergeOne(
            NamingEvidenceCandidate existing,
            NamingEvidenceCandidate candidate
    ) {
        if (existing == null) {
            return merger.merge(List.of(candidate)).get(0);
        }
        List<NamingEvidenceCandidate> both = new ArrayList<>(2);
        both.add(existing);
        both.add(candidate);
        return merger.merge(both).get(0);
    }

    private boolean sameEndpointPair(NamingEvidenceCandidate item, RelationshipCandidate candidate) {
        return (sameEndpoint(item.source(), candidate.source()) && sameEndpoint(item.target(), candidate.target()))
                || (sameEndpoint(item.source(), candidate.target()) && sameEndpoint(item.target(), candidate.source()));
    }

    private boolean sameEndpoint(
            com.relationdetector.contracts.model.Endpoint left,
            com.relationdetector.contracts.model.Endpoint right
    ) {
        return endpointKeys.same(left, right);
    }

    private String key(NamingEvidenceCandidate candidate) {
        return endpointKeys.factKey(candidate.source()) + "->"
                + endpointKeys.factKey(candidate.target()) + ":" + candidate.rule();
    }
}
