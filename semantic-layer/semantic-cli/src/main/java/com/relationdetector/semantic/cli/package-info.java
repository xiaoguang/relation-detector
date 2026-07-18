/**
 * CN: semantic CLI 包负责命令参数、分发、稳定退出码及 artifact 编排。输入是命令行和 relation-detector JSON，输出 KG、提取请求或规范文档；上游是用户/自动化，下游是 semantic-core services。禁止依赖 parser/adaptor、输出敏感异常内容或复制物理推断逻辑。
 * EN: The semantic CLI package owns command parsing, dispatch, stable exit codes, and artifact orchestration. It consumes user arguments and relation-detector JSON for semantic-core services; it must not depend on parsers/adaptors, expose sensitive failures, or duplicate physical inference.
 */
package com.relationdetector.semantic.cli;
