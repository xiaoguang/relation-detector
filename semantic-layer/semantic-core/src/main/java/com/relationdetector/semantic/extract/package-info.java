/**
 * CN: extract 包负责证据 bundle、模型请求、正式语义文档规范化、引用校验和审查项生成。输入是 typed scan/evidence 或模型 JSON，输出可审计 semantic artifact；上游是 reader/graph，下游是 CLI 和消费者。禁止无 evidence bundle 产出正式结果或补造物理端点。
 * EN: The extraction package owns evidence bundles, model requests, formal-document normalization, reference validation, and review generation. It consumes typed scan evidence or model JSON and emits auditable artifacts; it must not produce formal output without evidence or invent physical endpoints.
 */
package com.relationdetector.semantic.extract;
