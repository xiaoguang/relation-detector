package com.relationdetector.semantic.cli;

import com.relationdetector.semantic.extract.OpenAiResponsesSemanticExtractor;
import com.relationdetector.semantic.extract.SemanticExtractionArtifactWriter;
import com.relationdetector.semantic.extract.SemanticExtractionPrompt;
import com.relationdetector.semantic.extract.SemanticExtractionPromptBuilder;
import com.relationdetector.semantic.extract.SemanticExtractionResult;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 执行 extract 命令，生成 Codex 会话请求或调用 Responses API；输入是 scan bundle 和提取配置，输出是审计 artifact，禁止构造物理事实。
 * EN: Executes extraction by writing a Codex-session request or calling the Responses API; it emits auditable artifacts and never creates physical facts.
 */
final class SemanticExtractCommandHandler {
    int execute(SemanticCommandArguments arguments) {
        ScanBundle bundle = new SemanticScanBundleReader().read(arguments);
        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(
                bundle,
                arguments.focus(),
                arguments.maxRelationships(),
                arguments.maxLineage(),
                arguments.maxNamingEvidence());
        SemanticExtractionArtifactWriter writer = new SemanticExtractionArtifactWriter();
        if (arguments.provider() == SemanticExtractProvider.CODEX_SESSION) {
            writer.writeCodexSessionRequest(arguments.output(), prompt);
            return 0;
        }
        String apiKey = arguments.requestOnly() ? "" : arguments.apiKey();
        OpenAiResponsesSemanticExtractor extractor = new OpenAiResponsesSemanticExtractor(
                arguments.baseUrl(), apiKey, arguments.model(), arguments.reasoningEffort(),
                arguments.maxOutputTokens());
        if (arguments.requestOnly()) {
            writer.writeRequestOnly(arguments.output(), prompt, extractor.requestJson(prompt));
            return 0;
        }
        SemanticExtractionResult result = extractor.extract(prompt);
        writer.writeResult(arguments.output(), prompt, result);
        return 0;
    }
}
