package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一个 hunk。
 *
 * @param oldStart 旧文件起始行号
 * @param oldCount 旧文件行数
 * @param newStart 新文件起始行号
 * @param newCount 新文件行数
 * @param lines hunk 内的行
 */
public record Hunk(
    int oldStart, int oldCount, int newStart, int newCount, List<ChangedLine> lines) {}