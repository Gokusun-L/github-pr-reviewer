package com.gokusun.prreviewer.diff;

/**
 * diff 中的一行。content 不含 diff 前缀字符。
 *
 * @param oldLineNo 旧文件行号；该行不在旧文件侧时为 null
 * @param newLineNo 新文件行号；该行不在新文件侧时为 null
 */
public record ChangedLine(
    ChangeType type,
    Integer oldLineNo,
    Integer newLineNo,
    String content) {
}
