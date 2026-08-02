/**
 * CN: 责任是将MySQL database唯一映射到catalog并验证live scope；输入为ScanScope和JDBC connection，输出为
 * catalog-only namespace。上游是MySQL collectors，下游是catalog查询；禁止把database写入schema或跨库冒充结果。
 * EN: Responsibility: map a MySQL database uniquely to catalog and validate live scope. Inputs are ScanScope and JDBC
 * connection; output is a catalog-only namespace. Upstream is MySQL collectors and downstream is catalog queries.
 * Mapping databases to schema or relabeling cross-database results is forbidden.
 */
package com.relationdetector.mysql.namespace;
