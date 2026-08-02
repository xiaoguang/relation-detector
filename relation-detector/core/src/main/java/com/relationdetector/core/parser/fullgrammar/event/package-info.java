/**
 * CN: 责任是把full-grammar parse tree的typed事件投影为公共event模型；输入为typed context adapter，输出为
 * parser-neutral事件。上游是版本化grammar adapter，下游是事实提取；禁止读取raw SQL、调用其他版本parser或合并事实。
 * EN: Responsibility: project typed full-grammar tree events into parser-neutral event models. Inputs are typed context
 * adapters; outputs are structured events. Upstream is versioned grammar adapters and downstream is fact extraction.
 * Raw-SQL scanning, cross-version parser delegation, and fact merging are forbidden.
 */
package com.relationdetector.core.parser.fullgrammar.event;
