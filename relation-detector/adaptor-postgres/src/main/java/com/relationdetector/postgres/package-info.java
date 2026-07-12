/**
 * PostgreSQL adaptor 装配层。
 *
 * <p>CN: 本包暴露 PostgreSQL metadata、数据库 DDL、对象定义、日志、数据画像、
 * token-event parser 和 full-grammar module。根包只做装配；方言 parser 代码分别
 * 位于 tokenevent 与 fullgrammar/v16 子包。
 *
 * <p>EN: PostgreSQL adaptor assembly layer. It exposes PostgreSQL metadata,
 * database DDL, object definitions, logs, profiling, token-event parsers, and
 * full-grammar modules. The root package assembles services; dialect parser
 * code lives in tokenevent and fullgrammar/v16 subpackages.
 */
package com.relationdetector.postgres;
