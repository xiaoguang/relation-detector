package com.relationdetector.semantic.kg.store;

import java.util.List;

/**
 * CN: 保存一次KG序列化的逻辑文件摘要与闭包计数；上游是磁盘artifact writer，下游是manifest和验证runner，
 * 本record不重新读取或解释artifact内容。
 * EN: Captures logical file digests and closure counts for one KG serialization. It feeds manifests and
 * verification runners without reopening or interpreting the rendered artifacts.
 */
public record SemanticKgArtifactReport(List<ArtifactDigest> artifacts, Summary summary) {
    public SemanticKgArtifactReport {
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        if (artifacts.size() != 3 || summary == null) {
            throw new IllegalArgumentException("semantic KG artifact report is incomplete");
        }
    }

    public record ArtifactDigest(String path, long bytes, String sha256) {
        public ArtifactDigest {
            if (path == null || path.isBlank() || bytes < 0
                    || sha256 == null || sha256.length() != 64) {
                throw new IllegalArgumentException("semantic KG artifact digest is invalid");
            }
        }
    }

    public record Summary(long nodeCount, long edgeCount, long evidenceRefCount,
                          long diagnosticCount, String referenceClosure) {
        public Summary {
            if (nodeCount < 0 || edgeCount < 0 || evidenceRefCount < 0 || diagnosticCount < 0
                    || !"PASS".equals(referenceClosure)) {
                throw new IllegalArgumentException("semantic KG artifact summary is invalid");
            }
        }
    }
}
