package com.relationdetector.semantic.internal.store;

import java.nio.charset.StandardCharsets;

/** Shared unsigned UTF-8 byte order for external sorting and on-disk lookup. */
final class Utf8ByteOrder {
    private Utf8ByteOrder() {
    }

    static int compare(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return compare(leftBytes, leftBytes.length, rightBytes, rightBytes.length);
    }

    static int compare(byte[] left, int leftLength, byte[] right, int rightLength) {
        int length = Math.min(leftLength, rightLength);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(
                    Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftLength, rightLength);
    }
}
