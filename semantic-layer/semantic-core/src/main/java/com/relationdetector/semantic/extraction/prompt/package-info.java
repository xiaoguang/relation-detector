/**
 * CN: 责任是从有界owned shard或reconciliation选择构造模型prompt/request并执行保守token估算；输入为已验证
 * evidence slice，输出为有界请求。上游是shard/runtime，下游是model client；禁止绕过预算、读取raw SQL推断结构或合并结果。
 * EN: Responsibility: build bounded model prompts and requests from owned shards or reconciliation selections and
 * apply conservative token estimates. Inputs are validated evidence slices; outputs are bounded requests. Upstream is
 * sharding/runtime and downstream is model clients. Budget bypasses, raw-SQL inference, and result merging are forbidden.
 */
package com.relationdetector.semantic.extraction.prompt;
