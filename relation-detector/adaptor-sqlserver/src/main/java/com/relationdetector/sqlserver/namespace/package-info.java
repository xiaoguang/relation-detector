/**
 * CN: 责任是验证SQL Server catalog与当前connection database一致并保留schema；输入为scope/connection，输出为
 * 已证明namespace。上游是live collectors，下游是sys catalog查询；禁止隐式跨库读取或错误重标catalog。
 * EN: Responsibility: verify that SQL Server catalog matches the connected database while preserving schema. Inputs
 * are scope and connection; output is a proven namespace. Upstream is live collection and downstream is sys catalog.
 * Implicit cross-database reads and catalog relabeling are forbidden.
 */
package com.relationdetector.sqlserver.namespace;
