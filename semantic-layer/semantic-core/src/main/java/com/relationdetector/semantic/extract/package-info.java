/**
 * CN: extract 包负责证据 bundle、确定性分片、模型请求、稳定合并、受限协调、正式语义文档规范化和引用校验。输入是 typed scan/evidence 或模型 JSON，输出可审计 semantic artifact；上游是 reader/graph，下游是 CLI 和消费者。禁止把 KG 交给模型改写、无 evidence bundle 产出正式结果或补造物理端点。
 * EN: The extraction package owns evidence bundles, deterministic sharding, model requests, stable merging, constrained reconciliation, formal normalization, and reference validation. It consumes typed scan evidence or model JSON and emits auditable artifacts; it must not let a model rewrite the KG, produce formal output without evidence, or invent physical endpoints.
 */
package com.relationdetector.semantic.extract;
