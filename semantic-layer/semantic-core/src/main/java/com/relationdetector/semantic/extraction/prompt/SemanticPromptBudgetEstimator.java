package com.relationdetector.semantic.extraction.prompt;

/**
 * CN: 对最终 prompt 文本做保守、确定性的 token 预算估算；输入是已渲染文本，输出只用于切片门限，禁止影响语义或替代 API usage。
 * EN: Conservatively and deterministically estimates tokens for rendered prompt text. The estimate is only a sharding budget signal and never semantic input or a replacement for API usage.
 */
public final class SemanticPromptBudgetEstimator {
    private static final int FIXED_OVERHEAD = 64;

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return FIXED_OVERHEAD;
        }
        long ascii = 0;
        long nonAscii = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint <= 0x7f) {
                ascii++;
            } else {
                nonAscii++;
            }
            offset += Character.charCount(codePoint);
        }
        return estimate(ascii, nonAscii);
    }

    public static int estimate(long asciiCodePoints, long nonAsciiCodePoints) {
        if (asciiCodePoints < 0 || nonAsciiCodePoints < 0) {
            throw new IllegalArgumentException("semantic token-estimate counts cannot be negative");
        }
        long base = (asciiCodePoints + 3L) / 4L + nonAsciiCodePoints + FIXED_OVERHEAD;
        long withMargin = (base * 115L + 99L) / 100L;
        return withMargin > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) withMargin;
    }

    public static int minimumEstimateForUtf8Bytes(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("semantic input size cannot be negative");
        }
        long minimumBase = (bytes + 3L) / 4L + FIXED_OVERHEAD;
        long withMargin = (minimumBase * 115L + 99L) / 100L;
        return withMargin > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) withMargin;
    }

    public int estimate(SemanticExtractionPrompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("semantic prompt is required");
        }
        return estimate(prompt.developerPrompt() + "\n" + prompt.userPrompt());
    }
}
