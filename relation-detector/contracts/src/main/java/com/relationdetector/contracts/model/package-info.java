/**
 * 跨模块共享的领域模型层。
 *
 * <p>CN: 本包只定义 relationship、data lineage、endpoint、evidence 和 warning
 * 的数据结构，不执行 parser、评分、合并或数据库访问逻辑。contracts 之外的
 * 模块可以创建和读取这些模型，但不应在模型类里塞入方言判断或扫描流程。
 *
 * <p>EN: Shared domain model layer for relationships, data lineage, endpoints,
 * evidence, and warnings. This package is data-only: parser behavior, scoring,
 * merging, and database access live in core/adaptor modules, not in model classes.
 */
package com.relationdetector.contracts.model;
