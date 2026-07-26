/**
 * CN: 发布验证内部包。职责是从 correctness、sample-data 和本次会话的小型报告中流式校验结果、
 * 生成外存 canonical fingerprint、汇总报告并写 manifest。输入来自发布 shell 与已生成 artifact，
 * 输出供 release gate 和人工审计使用；上游是 isolated runners，下游是 verification session。
 * 禁止承担公开 CLI、SQL 解析、事实推断、golden 更新或业务数据解释。
 *
 * EN: Internal release-verification package. Its responsibility is to stream-validate correctness and sample-data
 * outputs, produce external-memory canonical fingerprints, aggregate compact reports, and write manifests.
 * Inputs come from release shells and generated artifacts; outputs serve release gates and human audit.
 * Isolated runners are upstream and the verification session is downstream. It must not expose public CLI
 * commands, parse SQL, infer facts, update golden files, or interpret business data.
 */
package com.relationdetector.cli.verification;
