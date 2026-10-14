package com.gokusun.prreviewer.diff;

/**
 * diff 中的一行。
 *
 * @param filePath 文件路径
 * @param type 变更类型
 * @param oldLineNo 旧文件行号；ADDED 行为 -1
 * @param newLineNo 新文件行号；REMOVED 行为 -1
 * @param content 行内容，不含 diff 前缀
 */
public record ChangedLine(
    String filePath, ChangeType type, int oldLineNo, int newLineNo, String content) {}