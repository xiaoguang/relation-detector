/**
 * CN: kg 包负责从 evidence graph 构建稳定节点、边和 JSON artifacts。输入是经过 enrichment 的证据图，输出交给 CLI 或外部图消费者；上游是 graph/enrich，下游是 writer。禁止重新解析 scan JSON、调用模型或静默覆盖冲突节点和边。
 * EN: The KG package builds stable nodes, edges, and JSON artifacts from an enriched evidence graph. It writes outputs for CLI and external graph consumers; it must not reparse scan JSON, call models, or silently overwrite conflicting nodes and edges.
 */
package com.relationdetector.semantic.kg;
