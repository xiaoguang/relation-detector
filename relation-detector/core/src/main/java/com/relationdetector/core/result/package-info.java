/**
 * CN: 责任是保存并最终装配scan事实、metadata inventory及summary；输入为各阶段已验证候选，输出为稳定
 * ScanResult。上游是采集、parser和evidence阶段，下游是JSON/table writer；禁止重新解析SQL、补证据或隐藏内部warning。
 * EN: Responsibility: own and assemble scan facts, metadata inventory, and summaries. Inputs are validated stage
 * candidates; output is a stable ScanResult. Upstream is collection/parser/evidence processing and downstream is
 * result writing. SQL reparsing, evidence invention, and internal-warning suppression are forbidden.
 */
package com.relationdetector.core.result;
