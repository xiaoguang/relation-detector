/**
 * 诊断告警工厂层。
 *
 * <p>CN: 本包统一构造 parser、日志、DDL、对象采集和扫描失败 warning，保证 code、
 * source、line 与 raw statement attributes 的格式一致。业务层不要各自拼装不兼容
 * 的 warning。
 *
 * <p>EN: Diagnostic warning factory layer. It standardizes parser, log, DDL,
 * object-collection, and scan-failure warnings so code, source, line, and raw
 * statement attributes stay consistent across the system.
 * <p>CN: parser/file warning 为本地语法审计可以保留 raw SQL、DDL 和原始异常文本；
 * live JDBC warning 则必须经过 {@code LiveDiagnosticSanitizer}，使用固定消息并禁止暴露
 * JDBC URL、SQL、driver message 或业务值。本包统一 warning 结构，但不把两类来源误写成
 * 相同的敏感信息策略。
 *
 * <p>EN: Parser/file warnings may retain raw SQL, DDL, and original exception text for local
 * syntax auditing. Live JDBC warnings must pass through {@code LiveDiagnosticSanitizer}, use
 * fixed messages, and must not expose a JDBC URL, SQL, driver messages, or business values.
 * This package standardizes warning structure without claiming one sensitivity policy for both
 * source families.
 *
 * <p>Inputs: typed parser/file failures, live SQLExceptions, and definition context.
 * <p>Outputs: warnings with consistent code, source, line, and source-appropriate attributes.
 * <p>Upstream/Downstream: parsers and collectors call these factories; ScanResult and CLI render the result.
 * <p>Forbidden: parser/file factories must not be used to bypass live JDBC sanitization.
 */
package com.relationdetector.core.diagnostics;
