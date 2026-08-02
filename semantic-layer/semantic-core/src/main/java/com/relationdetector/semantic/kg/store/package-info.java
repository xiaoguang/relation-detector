/**
 * CN: 责任是从磁盘evidence records流式构建、校验并写出KG artifact；输入为闭合Evidence Graph，输出为KG、
 * evidence graph及digest报告。上游是evidence store，下游是CLI/artifact发布；禁止依赖extraction runtime或复制证据payload。
 * EN: Responsibility: stream, validate, and write KG artifacts from disk evidence records. Input is a closed Evidence
 * Graph; outputs are KG, evidence graph, and digest reports. Upstream is evidence storage and downstream is CLI/artifact
 * publication. Extraction-runtime dependencies and duplicated evidence payloads are forbidden.
 */
package com.relationdetector.semantic.kg.store;
