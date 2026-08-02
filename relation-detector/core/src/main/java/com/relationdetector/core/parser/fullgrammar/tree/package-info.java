/**
 * CN: 责任是提供generated-context无关的typed tree访问契约和source location；输入为版本adapter提供的节点，
 * 输出为稳定tree视图。上游是方言adapter，下游是expression/event分析；禁止依赖具体generated类或扫描raw SQL。
 * EN: Responsibility: expose generated-context-neutral typed tree access and source locations. Inputs are nodes supplied
 * by version adapters; outputs are stable tree views. Upstream is dialect adapters and downstream is expression/event
 * analysis. Dependencies on concrete generated classes and raw-SQL scanning are forbidden.
 */
package com.relationdetector.core.parser.fullgrammar.tree;
