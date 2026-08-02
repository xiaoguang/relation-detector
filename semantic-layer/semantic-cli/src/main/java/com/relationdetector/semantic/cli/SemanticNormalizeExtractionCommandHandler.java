package com.relationdetector.semantic.cli;

import com.relationdetector.semantic.facade.SemanticNormalizationFacade;

/**
 * CN: 执行 normalize-extraction 命令并原子组装正式语义文档；输入是模型结果和 evidence bundle，输出规范 JSON，禁止在缺少证据时补造事实。
 * EN: Executes normalize-extraction from a model result and evidence bundle into formal JSON; it must never invent facts when evidence is absent.
 */
final class SemanticNormalizeExtractionCommandHandler {
    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        new SemanticNormalizationFacade().normalize(
                arguments.inputs().get(0), arguments.evidenceBundle(), arguments.output(),
                arguments.maxOutputTokens(), arguments.sharding().maxInputTokens());
        return SemanticCliExitCode.SUCCESS;
    }
}
