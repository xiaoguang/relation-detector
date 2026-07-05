package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.DerivedPathKind;

/**
 * Inferred transitive path fact.
 *
 * <p>CN: 这是由已确认的 relationship / Data Lineage / naming evidence 图推导出来的
 * 可达事实，不代表直接物理约束或直接字段写入。完整路径必须保存在 path 和 evidence 中，
 * 方便审计。</p>
 */
public final class DerivedPathCandidate {
    private final DerivedPathKind kind;
    private final Endpoint source;
    private final Endpoint target;
    private final List<Endpoint> path;
    private BigDecimal confidence = BigDecimal.ZERO;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Evidence> rawEvidence = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public DerivedPathCandidate(
            DerivedPathKind kind,
            Endpoint source,
            Endpoint target,
            List<Endpoint> path
    ) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (source == null || target == null) {
            throw new IllegalArgumentException("source and target are required");
        }
        if (path == null || path.size() < 3) {
            throw new IllegalArgumentException("derived path must contain at least three endpoints");
        }
        this.kind = kind;
        this.source = source;
        this.target = target;
        this.path = new ArrayList<>(path);
    }

    public DerivedPathKind kind() {
        return kind;
    }

    public Endpoint source() {
        return source;
    }

    public Endpoint target() {
        return target;
    }

    public List<Endpoint> path() {
        return path;
    }

    public int pathLength() {
        return path.size() - 1;
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public void confidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<Evidence> evidence() {
        return evidence;
    }

    public List<Evidence> rawEvidence() {
        return rawEvidence;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}
