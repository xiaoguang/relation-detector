/**
 * CN: 责任是持久化请求、分片审计、merge结果和run manifest，并执行hash、路径和原子发布校验；输入为已验证
 * plan/result，输出为可复核run目录。上游是runtime，下游是用户/verification；禁止调用模型或发布部分正式结果。
 * EN: Responsibility: persist requests, shard audits, merged results, and run manifests with hash, path, and atomic
 * publication checks. Inputs are validated plans/results; output is an auditable run directory. Upstream is runtime
 * and downstream is users/verification. Model calls and partially published formal results are forbidden.
 */
package com.relationdetector.semantic.extraction.artifact;
