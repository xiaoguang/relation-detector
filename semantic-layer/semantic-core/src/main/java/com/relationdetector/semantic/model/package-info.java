/**
 * CN: model 包保存不依赖 Jackson 的中性语义值对象，包括显式区分 table 与 column 的物理端点。输入由 reader 或明确工厂构造，输出供全部 semantic 子层使用；上游是结构化边界，下游是 graph/extract/KG。禁止猜测命名空间或读取 JSON。
 * EN: The model package holds Jackson-free semantic values, including physical references that explicitly distinguish tables from columns. Reader boundaries construct them for graph, extraction, and KG downstream use; models must not guess namespaces or read JSON.
 */
package com.relationdetector.semantic.model;
