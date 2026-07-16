/**
 * metadata evidence 增强层。
 *
 * <p>CN: 本包用 catalog facts 为 parser 产生的候选关系补充唯一性、索引和类型证据。
 * 它是 enhancer，不从 metadata 独立发明 parser 关系，也不改变 SQL/DDL 结构事件。
 *
 * <p>EN: Metadata evidence enhancement layer. It adds uniqueness, index, and
 * type evidence from catalog facts to parser-produced candidates. It is an
 * enhancer, not a standalone parser relationship generator.
 * <p>Responsibility: 用 live metadata inventory 增强已有 relationship evidence / Enhances existing relationships.
 * <p>Inputs: MetadataSnapshot 与 direct relationship candidates / Metadata snapshots and direct candidates.
 * <p>Outputs: 保留 observation provenance 的 metadata evidence / Metadata evidence with full provenance.
 * <p>Upstream/Downstream: adaptor metadata 上游，relationship merger 下游 / Between metadata collectors and merger.
 * <p>Forbidden: composite unique 不证明成员单列唯一，index 不单独创建关系 / Must not overstate composite evidence.
 */
package com.relationdetector.core.metadata;
