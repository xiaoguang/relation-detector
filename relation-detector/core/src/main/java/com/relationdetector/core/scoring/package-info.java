/**
 * relationship 置信度计算层。
 *
 * <p>CN: 本包根据 evidence base score、metadata 增强和重复观察计算最终
 * relationship confidence。Data Lineage confidence 是独立概念，不进入这里的
 * relationship 评分公式。
 *
 * <p>EN: Relationship confidence scoring layer. It computes final relationship
 * confidence from evidence base scores, metadata enhancement, and repeated
 * observations. Data Lineage confidence is separate and does not feed this
 * formula.
 */
package com.relationdetector.core.scoring;
