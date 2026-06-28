/**
 * PostgreSQL token-event parser 子包。
 *
 * <p>CN: 本包提供 PostgreSQL 生产 fallback parser 入口。SQL 路径优先运行
 * PostgreSQL typed structural grammar 和 parse-tree visitor，再显式附加 legacy
 * token-event supplement；DDL 路径仍复用 token-event DDL visitor。它不包含
 * full-grammer 版本化实现。
 *
 * <p>EN: PostgreSQL token-event parser package. It provides production fallback
 * parser entry points. The SQL path runs the PostgreSQL typed structural
 * grammar and parse-tree visitor first, then explicitly adds the legacy
 * token-event supplement. Versioned full-grammer implementation is not kept here.
 */
package com.relationdetector.postgres.tokenevent;
