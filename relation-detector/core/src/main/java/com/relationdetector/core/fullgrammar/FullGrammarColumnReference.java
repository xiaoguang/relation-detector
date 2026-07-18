package com.relationdetector.core.fullgrammar;

/**
 * CN: 承载 full-grammar typed context 解析出的 qualifier 与 column 片段，尚未解析为物理 endpoint。
 * EN: Carries qualifier and column segments selected from a full-grammar typed context before physical endpoint resolution.
 */
public record FullGrammarColumnReference(String qualifier, String column) {
}
