package com.relationdetector.semantic.cli;

import com.relationdetector.semantic.reader.ScanBundle;
import com.relationdetector.semantic.reader.ScanResultReader;

/**
 * CN: 按 CLI 输入列表读取单份或合并后的 scan bundle；上游是各命令 handler，下游是严格的 ScanResultReader，禁止改变合并身份规则。
 * EN: Reads one or merged scan bundles for command handlers through the strict ScanResultReader; it must not alter merge identity rules.
 */
final class SemanticScanBundleReader {
    ScanBundle read(SemanticCommandArguments arguments) {
        ScanResultReader reader = new ScanResultReader();
        return arguments.inputs().size() == 1
                ? reader.read(arguments.inputs().get(0))
                : reader.readMerged(arguments.inputs());
    }
}
