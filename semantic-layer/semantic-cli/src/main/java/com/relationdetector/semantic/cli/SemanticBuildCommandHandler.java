package com.relationdetector.semantic.cli;

/**
 * CN: 执行 build 命令并调用共享 KG build service；输入是已验证 CLI 参数，副作用是写入 KG artifacts，禁止复制证据图装配流程。
 * EN: Executes the build command through the shared KG build service; it writes KG artifacts and must not duplicate graph assembly.
 */
final class SemanticBuildCommandHandler {
    int execute(SemanticCommandArguments arguments) {
        new SemanticKgBuildService().build(new SemanticScanBundleReader().read(arguments), arguments.output());
        return 0;
    }
}
