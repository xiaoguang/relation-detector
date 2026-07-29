/**
 * CN: reader 包的职责是流式验证 relation-detector JSON，将完整 metadata inventory 与事实写入
 * 磁盘后备 section/index，并按需物化有界 ScanBundle 输入窗口。输入是一个或多个
 * relation-detector artifact，输出是已验证的输入存储、全局证据存储和运输窗口。上游是
 * relation-detector 输出，下游是 event、graph 与 extract。禁止职责包括容忍未知枚举、猜测端点层级、
 * 接受非 COMPLETE inventory、把窗口当作语义边界、完整物化无界输入或返回部分验证结果。
 *
 * EN: The reader package responsibility is to stream-validate one or more relation-detector artifacts as inputs,
 * spool their complete metadata inventory and facts to disk-backed sections and indexes, and return validated
 * stores and bounded ScanBundle input windows as outputs. Its upstream is relation-detector output and its downstream
 * consumers are event, graph, and extract. It must not accept unknown enums, guess endpoint levels, accept
 * incomplete inventories, treat windows as semantic boundaries, materialize an unbounded input in full, or return
 * partially validated results.
 */
package com.relationdetector.semantic.reader;
