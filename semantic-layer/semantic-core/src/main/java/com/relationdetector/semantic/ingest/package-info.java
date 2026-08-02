/**
 * CN: 责任是流式读取relation-detector结果并建立闭合、磁盘后备的typed输入inventory；输入为scan JSON路径，
 * 输出为SemanticInputStore。上游是CLI facade，下游是evidence聚合；禁止调用模型、生成业务语义或完整物化输入。
 * EN: Responsibility: stream relation-detector results into a closed, disk-backed typed input inventory. Inputs are
 * scan JSON paths; output is SemanticInputStore. Upstream is CLI facades and downstream is evidence aggregation.
 * Model calls, business-semantic generation, and full input materialization are forbidden.
 */
package com.relationdetector.semantic.ingest;
