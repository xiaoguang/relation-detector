/**
 * CN: extract.model 包定义正式 semantic extraction 文档的传输模型。输入由 codec/normalizer 填充，输出交给 validator、graph assembler 和 artifact writer；上游是 extract pipeline，下游是序列化消费者。禁止包含 parser、网络调用或物理事实推断逻辑。
 * EN: The extraction model package defines the transport shape of formal semantic documents. Codecs and normalizers populate these values for validators, graph assembly, and artifact writers downstream; models must not contain parser, network, or physical-fact inference logic.
 */
package com.relationdetector.semantic.extract.model;
