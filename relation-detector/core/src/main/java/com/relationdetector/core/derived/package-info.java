/**
 * CN: relationship、lineage 与 naming 的 derived path 图推导。
 *
 * <p>EN: Derived-path graph inference for relationships, lineage, and naming.
 * <p>Responsibility: 从 direct facts 构建受约束的 derived paths / Builds constrained derived paths from direct facts.
 * <p>Inputs: 已合并的 relationship、VALUE lineage 与 naming evidence / Merged relationships, VALUE lineage, and naming.
 * <p>Outputs: 独立标识的 derived relationship/lineage/naming facts / Separately identified derived facts.
 * <p>Upstream/Downstream: direct merger 之后、result writer 之前 / Runs after direct merge and before output.
 * <p>Forbidden: 不把 conditional edges 或 CONTROL lineage 纳入闭包 / Must not close over conditional or CONTROL edges.
 */
package com.relationdetector.core.derived;
