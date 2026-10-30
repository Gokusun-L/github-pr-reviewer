package com.gokusun.prreviewer.diff;

import java.util.ArrayList;
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

  /**
   * 给每行补上原文件行号。行号是 (起点, 类型序列) 的派生结果，不存字段。
   */
  public List<AnnotatedLine> annotated() {
    List<AnnotatedLine> out = new ArrayList<>(lines.size());
    int oldNo = oldStart;
    int newNo = newStart;
    for (DiffLine line : lines) {
      LineType type = line.type();
      int oldLine = 0;
      int newLine = 0;
      if (type == LineType.CONTEXT) {
        oldLine = oldNo;
        newLine = newNo;
      }
      if (type == LineType.ADDED) {
        newLine = newNo;
      }
      if (type == LineType.REMOVED) {
        oldLine = oldNo;
      }
      out.add(new AnnotatedLine(line, oldLine, newLine));

      if (type == LineType.CONTEXT) {
        oldNo++;
        newNo++;
      } else if (type == LineType.ADDED) {
        newNo++;
      } else if (type == LineType.REMOVED) {
        oldNo++;
      }
    }
    return out;
  }
}
