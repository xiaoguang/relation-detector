/**
 * CN: reader 包负责验证 relation-detector JSON wire contract，并一次性转换为 typed ScanBundle 和 PhysicalEndpointRef。输入是 scan JSON 文件，输出交给 event/graph/extract；上游是 relation-detector artifact，下游是 semantic pipeline。禁止容忍未知枚举、猜测端点层级或返回部分结果。
 * EN: The reader package validates the relation-detector JSON wire contract and converts it once into typed ScanBundle and physical endpoint values. It feeds event, graph, and extraction downstream layers and must not accept unknown enums, guess endpoint levels, or return partial results.
 */
package com.relationdetector.semantic.reader;
