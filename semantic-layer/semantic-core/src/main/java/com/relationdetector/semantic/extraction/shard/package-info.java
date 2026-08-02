/**
 * CN: 责任是基于typed dependency、全局owner和保守预算生成path-backed shard plan；输入为完整evidence store，
 * 输出为owned/overlap闭合descriptor。上游是processing session，下游是prompt/runtime；禁止按raw字节任意切事实或让overlap拥有对象。
 * EN: Responsibility: create path-backed shard plans from typed dependencies, global owners, and conservative budgets.
 * Input is the complete evidence store; outputs are closed owned/overlap descriptors. Upstream is the processing
 * session and downstream is prompt/runtime. Arbitrary byte slicing and overlap ownership are forbidden.
 */
package com.relationdetector.semantic.extraction.shard;
