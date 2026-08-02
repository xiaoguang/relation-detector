/**
 * CN: 责任是解析合并后的运行配置并执行统一语义校验；输入为direct/CLI已映射配置和基准目录，输出为可执行
 * immutable配置。上游是CLI/direct API，下游是ScanEngine；禁止执行I/O采集、选择parser实现或吞掉配置错误。
 * EN: Responsibility: resolve merged runtime configuration and enforce one semantic validation contract. Inputs are
 * mapped direct/CLI settings and a base directory; output is executable immutable configuration. Upstream is CLI or
 * direct API and downstream is ScanEngine. Collection, parser implementation selection, and error suppression are forbidden.
 */
package com.relationdetector.core.config;
