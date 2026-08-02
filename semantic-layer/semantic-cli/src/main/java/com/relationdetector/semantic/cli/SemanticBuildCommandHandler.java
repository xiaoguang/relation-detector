package com.relationdetector.semantic.cli;

import com.relationdetector.semantic.facade.SemanticKgFacade;

/**
 * CN: 执行 build 命令并调用 semantic-core KG facade；输入是已验证 CLI 参数，副作用是写入 KG artifacts，
 * 禁止依赖或复制磁盘session与证据图装配流程。
 * EN: Executes build through the semantic-core KG facade. It writes KG artifacts from validated CLI input and must
 * not depend on or duplicate disk-session and evidence-graph assembly internals.
 */
final class SemanticBuildCommandHandler {
    SemanticCliExitCode execute(SemanticCommandArguments arguments) {
        new SemanticKgFacade().build(
                arguments.inputs(), arguments.output(), arguments.sharding().maxInputTokens(),
                arguments.kgOutput());
        return SemanticCliExitCode.SUCCESS;
    }
}
