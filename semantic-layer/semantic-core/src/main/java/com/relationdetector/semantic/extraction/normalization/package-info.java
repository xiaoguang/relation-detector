/**
 * CN: 责任是校验模型输出所有权、规范化canonical identity、重写引用并建立最终semantic graph；输入为有界模型
 * 文档和闭合evidence slice，输出为原子规范化结果。上游是runtime，下游是artifact/KG；禁止发明物理事实或静默覆盖冲突ID。
 * EN: Responsibility: validate model-output ownership, normalize canonical identity, rewrite references, and assemble
 * the final semantic graph. Inputs are bounded model documents and closed evidence slices; output is an atomic
 * normalized result. Upstream is runtime and downstream is artifacts/KG. Invented physical facts and silent ID overwrite are forbidden.
 */
package com.relationdetector.semantic.extraction.normalization;
