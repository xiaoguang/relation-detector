/**
 * metadata evidence 增强层。
 *
 * <p>CN: 本包用 catalog facts 为 parser 产生的候选关系补充唯一性、索引和类型证据。
 * 它是 enhancer，不从 metadata 独立发明 parser 关系，也不改变 SQL/DDL 结构事件。
 *
 * <p>EN: Metadata evidence enhancement layer. It adds uniqueness, index, and
 * type evidence from catalog facts to parser-produced candidates. It is an
 * enhancer, not a standalone parser relationship generator.
 */
package com.relationdetector.core.metadata;
