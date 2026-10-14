package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一个文件的 diff。
 *
 * @param path 文件路径
 * @param hunks hunk 列表
 */
public record FileDiff(String path, List<Hunk> hunks) {}