/**
 * CN: 责任是验证PostgreSQL catalog与当前connection database一致并解析schema；输入为scope/connection，输出为
 * 已证明namespace。上游是live collectors，下游是pg_catalog查询；禁止隐式跨库查询或错误标记catalog。
 * EN: Responsibility: prove that PostgreSQL catalog matches the connected database and resolve schema. Inputs are
 * scope and connection; output is a verified namespace. Upstream is live collection and downstream is pg_catalog.
 * Implicit cross-database queries and catalog relabeling are forbidden.
 */
package com.relationdetector.postgres.namespace;
