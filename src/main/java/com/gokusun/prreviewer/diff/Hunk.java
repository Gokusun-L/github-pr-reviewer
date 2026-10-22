package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一个 hunk，对应 diff 里的一段 @@ 头及其正文。
 *
 * @param oldStart 旧文件起始行号
 * @param oldCount 旧文件行数
 * @param newStart 新文件起始行号
 * @param newCount 新文件行数
 * @param section  @@ 头之后的分段说明；没有时为 null
 */
public record Hunk(
    int oldStart,
    int oldCount,
    int newStart,
    int newCount,
    String section,
    List<ChangedLine> lines) {

  public Hunk {
    lines = List.copyOf(lines);
  }
}
