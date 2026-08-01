package com.relationdetector.semantic.extract;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 以固定section顺序和长度分隔canonical JSON计算可重建request bundle摘要；输入一次只含一条记录，
 * 输出section及bundle SHA-256。本类不写artifact，也不改变数组顺序或semantic identity。
 * EN: Computes reconstructable request-bundle digests from length-delimited canonical JSON in fixed section order.
 * It retains one record at a time and emits section and bundle SHA-256 values without writing artifacts, reordering
 * arrays, or changing semantic identity.
 */
final class SemanticRequestBundleCanonicalDigest {
    private SemanticRequestBundleCanonicalDigest() {
    }

    static Accumulator accumulator() {
        return new Accumulator();
    }

    static String bundleSha256(JsonNode descriptor, Map<String, SectionDigest> sections) {
        if (descriptor == null || sections == null) {
            throw new IllegalArgumentException("semantic request bundle descriptor and sections are required");
        }
        Accumulator digest = accumulator();
        digest.add(descriptor);
        for (SemanticEvidenceStore.Section section : SemanticEvidenceStore.Section.values()) {
            SectionDigest value = sections.get(section.wireName());
            if (value == null) {
                throw new SemanticExtractionValidationException(
                        "semantic request bundle section digest is missing");
            }
            digest.add(TextNode.valueOf(section.wireName()));
            digest.add(TextNode.valueOf(Long.toString(value.count())));
            digest.add(TextNode.valueOf(value.sha256()));
        }
        return digest.finish().sha256();
    }

    static Map<String, SectionDigest> immutable(Map<String, SectionDigest> sections) {
        return Map.copyOf(new LinkedHashMap<>(sections));
    }

    static final class Accumulator {
        private final MessageDigest digest = sha256();
        private long count;
        private boolean finished;

        void add(JsonNode value) {
            if (finished || value == null) {
                throw new IllegalStateException("semantic request digest is not writable");
            }
            byte[] bytes = StableSemanticId.canonicalJson(value).getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
            digest.update(bytes);
            count++;
        }

        SectionDigest finish() {
            if (finished) {
                throw new IllegalStateException("semantic request digest is already finished");
            }
            finished = true;
            return new SectionDigest(count, java.util.HexFormat.of().formatHex(digest.digest()));
        }
    }

    record SectionDigest(long count, String sha256) {
        SectionDigest {
            if (count < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("semantic request section digest is invalid");
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
