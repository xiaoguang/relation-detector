/**
 * PostgreSQL token-event parser 子包。
 *
 * <p>CN: 本包提供 PostgreSQL 生产 fallback parser 入口。SQL/DDL 路径运行
 * PostgreSQL typed structural grammar 和 parse-tree visitor，并通过 core token-event
 * helper 发射结构事件。它不包含 full-grammer 版本化实现。
 *
 * <p>EN: PostgreSQL token-event parser package. It provides production fallback
 * parser entry points. SQL/DDL paths run the PostgreSQL typed structural
 * grammar and parse-tree visitor and emit events through shared core token-event
 * helpers. Versioned full-grammer implementation is not kept here.
 */
package com.relationdetector.postgres.tokenevent;
