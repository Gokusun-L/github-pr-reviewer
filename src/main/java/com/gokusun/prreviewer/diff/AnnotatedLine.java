package com.gokusun.prreviewer.diff;

/**
 * 带行号的一行。行号 1-based；{@code 0} 表示这一行在该侧不存在
 * （ADDED 行没有旧号，REMOVED 行没有新号）。
 */
public record AnnotatedLine(DiffLine line, int oldLine, int newLine) {
}
