/**
 * CN: lineage 内部 projection、scope 与 source trace 模型。
 *
 * <p>EN: Internal lineage projection, scope, and source-trace models.
 * <p>Responsibility: 保存 lineage resolver 的内部不可变模型 / Holds immutable internal lineage resolver models.
 * <p>Inputs: typed projection、write mapping 与 source traces / Typed projections, writes, and source traces.
 * <p>Outputs: resolver/extractor 之间的 scope 与 mapping values / Scope and mapping values shared internally.
 * <p>Upstream/Downstream: expression analysis 上游，lineage extraction 下游 / Between analysis and extraction.
 * <p>Forbidden: 不暴露为 JSON 契约或执行 parser traversal / Must not become JSON contracts or traverse parsers.
 */
package com.relationdetector.core.lineage.model;
