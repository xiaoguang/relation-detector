package com.relationdetector.core.scan;

/**
 * CN: 表示配置中的文件、目录或 glob 无法解析为可读 scan 输入。
 *
 * <p>EN: Signals that configured files, directories, or globs cannot be
 * resolved to readable scan input.
 */
public final class ScanInputException extends IllegalArgumentException {
    public ScanInputException(String message) {
        super(message);
    }

    public ScanInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
