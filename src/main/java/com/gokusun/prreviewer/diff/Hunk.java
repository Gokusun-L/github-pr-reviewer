package com.gokusun.prreviewer.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 hunk，对应 diff 里的一段 @@ 头及其正文。
 *
 * <p>
 * {@code lines} 是构造时快照，只读：通过 {@link #lines()} 写入会被拒绝，
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

  public List<AnnotatedLine> annotated() {
    List<AnnotatedLine> out = new ArrayList<>(lines.size());
    int oldNo = oldStart;
    int newNo = newStart;
    for (DiffLine line : lines) {

      // TODO 你写：
      // ① 用「当前」oldNo / newNo 造 AnnotatedLine 并 add
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

      // ② 按类型自增：CONTEXT 两个都 +1；ADDED 只 newNo++；REMOVED 只 oldNo++
      // ③ 该侧不存在时记 0（不是记当前值）

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
