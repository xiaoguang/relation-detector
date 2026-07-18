/**
 * CN: semantic-layer 根包负责稳定标识和跨子包公共语义工具。输入来自已验证的 relation-detector scan bundle，输出交给事件、证据图、抽取和 KG 子层；上游是 reader， 下游是各语义 builder。禁止在此解析 SQL、改写物理事实或引入 adaptor 依赖。
 * EN: The semantic root package owns stable identifiers and cross-package semantic utilities. It receives validated scan bundles and serves event, evidence, extraction, and KG downstream layers; it must not parse SQL, rewrite physical facts, or depend on adaptors.
 */
package com.relationdetector.semantic;
