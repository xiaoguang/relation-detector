/**
 * CN: 责任是解析并校验semantic extraction的模型、预算、分片和retention配置；输入为YAML/CLI映射值，输出为
 * immutable运行配置。上游是semantic CLI，下游是runtime facade；禁止读取scan事实、调用模型或静默接受未知字段。
 * EN: Responsibility: parse and validate model, budget, shard, and retention settings for semantic extraction. Inputs
 * are mapped YAML/CLI values; output is immutable runtime configuration. Upstream is semantic CLI and downstream is
 * runtime facades. Reading scan facts, model calls, and silently accepted unknown fields are forbidden.
 */
package com.relationdetector.semantic.extraction.config;
