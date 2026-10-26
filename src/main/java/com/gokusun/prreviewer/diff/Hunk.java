package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一个 hunk，对应 diff 里的一段 @@ 头及其正文。
 */
public record Hunk(
    int oldStart,
    int oldCount,
    int newStart,
    int newCount,
    List<DiffLine> lines) {
}
