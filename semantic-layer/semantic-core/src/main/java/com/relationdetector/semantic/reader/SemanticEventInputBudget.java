package com.relationdetector.semantic.reader;

import com.relationdetector.semantic.extract.SemanticShardingException;

/**
 * CN: 将semantic event的输入token门限转换为唯一的保守序列化字节预算，并在任何event成员或关联引用
 * 列表物化前验证base与additional closure；输入是非负估算值，输出仅为门禁结果，不改变候选语义。
 * EN: Converts the semantic event input-token limit into the single conservative serialized-byte budget used
 * before materializing event members or association references. It validates base and additional closure sizes
 * without changing candidate semantics.
 */
final class SemanticEventInputBudget {
    private static final long SERIALIZED_BYTES_PER_ESTIMATED_TOKEN = 3L;

    private SemanticEventInputBudget() {
    }

    static long maximumSerializedBytes(int maxInputTokens) {
        if (maxInputTokens <= 0) {
            throw new IllegalArgumentException("semantic event input budget must be positive");
        }
        return Math.multiplyExact((long) maxInputTokens, SERIALIZED_BYTES_PER_ESTIMATED_TOKEN);
    }

    static void requireWithin(
            long baseSerializedBytes,
            long additionalSerializedBytes,
            int maxInputTokens
    ) {
        if (baseSerializedBytes < 0 || additionalSerializedBytes < 0) {
            throw new IllegalArgumentException("semantic event size estimates cannot be negative");
        }
        long maximumBytes = maximumSerializedBytes(maxInputTokens);
        if (baseSerializedBytes > maximumBytes
                || additionalSerializedBytes > maximumBytes - baseSerializedBytes) {
            throw new SemanticShardingException(
                    "semantic event closure exceeds maximum input budget");
        }
    }
}
