package com.relationdetector.semantic.cli;

/**
 * CN: 将已验证参数分发给唯一命令 handler；上游是 Main，下游是 build/extract/e2e/normalize handler，禁止实现命令业务逻辑。
 * EN: Dispatches validated arguments to exactly one build, extract, e2e, or normalize handler; command behavior must stay outside this dispatcher.
 */
final class SemanticCommandDispatcher {
    int execute(SemanticCommandArguments arguments) {
        return switch (arguments.command()) {
            case BUILD -> new SemanticBuildCommandHandler().execute(arguments);
            case EXTRACT -> new SemanticExtractCommandHandler().execute(arguments);
            case E2E -> new SemanticE2eCommandHandler().execute(arguments);
            case NORMALIZE_EXTRACTION -> new SemanticNormalizeExtractionCommandHandler().execute(arguments);
        };
    }
}
