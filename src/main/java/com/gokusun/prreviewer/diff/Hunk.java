package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一个 hunk，对应 diff 里的一段 @@ 头及其正文。
 *
 * <p>{@code lines} 是构造时快照，只读：通过 {@link #lines()} 写入会被拒绝，
 * 构造后修改传入的集合也不影响本对象。
 */
public record Hunk(
    int oldStart,
    int oldCount,
    int newStart,
    int newCount,
    List<DiffLine> lines) {

  public Hunk {
    lines = List.copyOf(lines);
  }
}
