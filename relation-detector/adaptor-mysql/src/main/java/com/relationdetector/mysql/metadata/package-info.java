/**
 * CN: MySQL 实时 catalog metadata 采集与事实装配。
 *
 * <p>EN: MySQL live catalog-metadata collection and fact assembly.
 * <p>Responsibility: 从 information_schema 采集 catalog metadata / Collects MySQL catalog metadata.
 * <p>Inputs: canonical MySQL catalog scope and JDBC connection / Canonical scope and connection.
 * <p>Outputs: tables、columns、constraints、indexes and FK candidates / Metadata facts and FK candidates.
 * <p>Upstream/Downstream: core live scan 调用，metadata enhancer 消费 / Called by scan and consumed by enhancement.
 * <p>Forbidden: database 不得写入 schema 或 normalizedName / Must not place the database in schema or duplicate it.
 */
package com.relationdetector.mysql.metadata;
