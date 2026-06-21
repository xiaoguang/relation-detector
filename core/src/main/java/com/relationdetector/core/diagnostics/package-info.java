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
 */
package com.relationdetector.core.diagnostics;
