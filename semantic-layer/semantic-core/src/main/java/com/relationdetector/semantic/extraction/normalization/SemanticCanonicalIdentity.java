package com.relationdetector.semantic.extraction.normalization;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 从完整物理身份或规范化语义字段生成formal normalization与跨分片合并共用的稳定ID。输入是typed
 * semantic字段，输出是长度分隔SHA-256身份；本类不读取展示slug、不使用数组位置，也不验证evidence闭包。
 *
 * EN: Generates stable identities shared by formal normalization and cross-shard merging from complete physical or
 * normalized semantic fields. It consumes typed semantic fields and emits length-delimited SHA-256 identities
 * without display slugs or array positions; evidence closure remains outside this class.
 */
public final class SemanticCanonicalIdentity {
    private SemanticCanonicalIdentity() {
    }

    public static EntityIdentity entity(
            String physicalName,
            String name,
            String machineType,
            String type,
            List<String> ownedGroundingRefs
    ) {
        String physical = text(physicalName);
        String normalizedName = normalizeText(name);
        String normalizedType = normalizeText(firstNonBlank(machineType, type));
        if (!physical.isBlank()) {
            return new EntityIdentity(
                    "physical:" + physical,
                    StableSemanticId.of("entity-physical", physical),
                    normalizedName,
                    normalizedType,
                    List.of(),
                    true);
        }
        List<String> grounding = canonicalReferences(ownedGroundingRefs);
        if (grounding.isEmpty()) {
            throw new SemanticExtractionValidationException(
                    "pure business semantic entity requires owned grounding");
        }
        if (normalizedName.isBlank() || normalizedType.isBlank()) {
            throw new SemanticExtractionValidationException(
                    "pure business semantic entity requires name and type");
        }
        String signature = String.join("\u001f", grounding);
        return new EntityIdentity(
                "business:" + normalizedName + "\u0000" + normalizedType + "\u0000" + signature,
                StableSemanticId.of("entity-business", normalizedName, normalizedType, signature),
                normalizedName,
                normalizedType,
                grounding,
                false);
    }

    public static String event(String eventCandidateRef) {
        return StableSemanticId.of("event", text(eventCandidateRef));
    }

    public static String metric(
            String name,
            String machineType,
            String type,
            String physicalField,
            List<String> sourceFields
    ) {
        return StableSemanticId.of(
                "metric",
                normalizeText(name),
                normalizeText(firstNonBlank(machineType, type)),
                text(physicalField),
                String.join("\u001f", canonicalReferences(sourceFields)));
    }

    public static String dimension(
            String name,
            String machineType,
            String type,
            String physicalField,
            String dimensionTable
    ) {
        return StableSemanticId.of(
                "dimension",
                normalizeText(name),
                normalizeText(firstNonBlank(machineType, type)),
                text(physicalField),
                text(dimensionTable));
    }

    public static String review(String targetRef, String targetSection, String type) {
        return StableSemanticId.of("review", text(targetRef), text(targetSection), text(type));
    }

    public static String edge(String prefix, String source, String target, String type) {
        return StableSemanticId.of(
                "semantic-edge", text(prefix), text(source), text(target), text(type));
    }

    public static String ownedEdge(String prefix, String owner, String source, String target, String type) {
        return StableSemanticId.of(
                "semantic-edge", text(prefix), text(owner), text(source), text(target), text(type));
    }

    private static List<String> canonicalReferences(List<String> values) {
        return (values == null ? List.<String>of() : values).stream()
                .map(SemanticCanonicalIdentity::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static String normalizeText(String value) {
        return Normalizer.normalize(text(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record EntityIdentity(
            String key,
            String canonicalId,
            String name,
            String type,
            List<String> grounding,
            boolean physical
    ) {
    }
}
