/**
 * CN: 责任是分析typed expression tree并生成值来源与transform；输入为parser-neutral tree adapter，输出为
 * ExpressionTrace。上游是版本化full grammar，下游是lineage/relationship事件；禁止regex、token span猜测和名称规则。
 * EN: Responsibility: analyze typed expression trees into value sources and transforms. Inputs are parser-neutral tree
 * adapters; outputs are ExpressionTrace values. Upstream is versioned full grammar and downstream is lineage/relation
 * events. Regex, token-span guessing, and naming heuristics are forbidden.
 */
package com.relationdetector.core.parser.fullgrammar.expression;
