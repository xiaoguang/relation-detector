/**
 * CN: SQL Server adaptor 组合、catalog identity 与版本 profile 入口。
 *
 * <p>EN: SQL Server adaptor composition, catalog identity, and version-profile entry points.
 * <p>Responsibility: 装配 SQL Server capabilities、collectors、parsers 与 catalog rules / Assembles the adaptor.
 * <p>Inputs: ScanScope、JDBC connection and framed T-SQL / Scope, connection, and framed T-SQL.
 * <p>Outputs: SPI v6 SQL Server implementations / SQL Server collector and parser implementations.
 * <p>Upstream/Downstream: core SPI 上游，SQL Server 子包实现下游 / Connects core SPI to adaptor subpackages.
 * <p>Forbidden: 不隐式跨 database 或重复 fact merge / Must not query another database or duplicate fact merge.
 */
package com.relationdetector.sqlserver;
