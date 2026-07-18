package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.extract.SemanticExtractionArtifactWriter;
import com.relationdetector.semantic.extract.SemanticExtractionPrompt;
import com.relationdetector.semantic.extract.SemanticExtractionPromptBuilder;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 执行确定性 e2e 命令，复用 KG service 并生成本地提取请求；输入是 scan bundle，输出两组 artifact，禁止调用外部模型。
 * EN: Executes deterministic e2e generation by reusing the KG service and writing a local extraction request; it never calls an external model.
 */
final class SemanticE2eCommandHandler {
    int execute(SemanticCommandArguments arguments) {
        ScanBundle bundle = new SemanticScanBundleReader().read(arguments);
        String name = arguments.name().isBlank() ? defaultName(arguments.inputs().get(0)) : arguments.name();
        Path kgOutput = arguments.output().resolve("semantic-kg").resolve(name);
        Path extractionOutput = arguments.output().resolve("semantic-extraction").resolve(name);
        new SemanticKgBuildService().build(bundle, kgOutput);
        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(
                bundle, arguments.focus(), arguments.maxRelationships(), arguments.maxLineage(),
                arguments.maxNamingEvidence());
        new SemanticExtractionArtifactWriter().writeCodexSessionRequest(extractionOutput, prompt);
        return 0;
    }

    private String defaultName(Path input) {
        String fileName = input.getFileName() == null ? "scan-result" : input.getFileName().toString();
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }
}
