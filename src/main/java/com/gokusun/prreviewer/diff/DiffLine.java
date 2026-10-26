package com.gokusun.prreviewer.diff;

/**
 * diff 中的一行。content 不含行首的分类字符。
 */
public record DiffLine(LineType type, String content) {
}
