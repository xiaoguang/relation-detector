/**
 * CN: statement、object 与 observation 的 canonical provenance。
 *
 * <p>EN: Canonical statement, object, and observation provenance.
 * <p>Responsibility: 复制、规范化并验证 fact/evidence 的 source provenance / Maps and validates source provenance.
 * <p>Inputs: statement/event file、object、block、statement 与 line values / Statement and event source coordinates.
 * <p>Outputs: repo-relative、范围合法的 provenance attributes / Repo-relative, range-valid provenance attributes.
 * <p>Upstream/Downstream: parser/extractor 上游，merger/output/audit 下游 / Between extraction and audit/output.
 * <p>Forbidden: 不拼接多个 observation 的冲突来源或保留绝对路径 / Must not mix sources or retain absolute paths.
 */
package com.relationdetector.core.provenance;
