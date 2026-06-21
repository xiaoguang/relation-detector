/**
 * full-grammer 通用基础设施层。
 *
 * <p>CN: 本包负责版本化 grammar profile 选择、module 注册、parser factory、
 * parity 比较和 typed visitor 共享辅助。具体 MySQL/PostgreSQL grammar、visitor 和
 * parser 实现属于 adaptor 模块。full-grammer 只替换事件来源；relationship / lineage
 * 语义仍由 core.relation 与 core.lineage 共享处理。
 *
 * <p>EN: Shared full-grammer infrastructure for versioned grammar profile
 * selection, module registration, parser factories, parity comparison, and
 * typed visitor helpers. Concrete dialect grammars and visitors live in adaptor
 * modules. Full-grammer replaces the event source only; relationship and
 * lineage semantics stay shared in core.relation and core.lineage.
 */
package com.relationdetector.core.fullgrammer;
