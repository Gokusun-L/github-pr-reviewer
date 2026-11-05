package com.gokusun.prreviewer.review;

import com.gokusun.prreviewer.diff.AnnotatedLine;
import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.Hunk;
import com.gokusun.prreviewer.diff.LineType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把模型的"说法"落到 diff 的"事实"上。
 *
 * <p><b>这个类故意没有字段。</b> 所有判断都从传入的 List&lt;DiffFile&gt; 现算。
 * 一旦把行号索引缓存成字段，diff 换了它不会跟着变 —— 同一 JVM 跑第二个 PR 时
 * 整批 finding 会被误拒，而且没有任何测试会红。
 *
 * <p>纯函数：同样入参出参恒定，所以能直接断言，不需要任何 mock。
 */
public final class FindingValidator {

  /**
   * 逐条判定候选 finding；每条命中即返回，不继续往下判。
   *
   * <p>保留的按 (filePath, line) 升序稳定排序；丢弃的保持模型原序。
   */
  public ValidationOutcome validate(List<DiffFile> files, List<Finding> candidates) {
    List<Finding> kept = new ArrayList<>();
    List<RejectedFinding> rejected = new ArrayList<>();

    for (Finding candidate : candidates) {
      FindingRejectReason reason = reasonFor(files, candidate);
      if (reason == null) {
        kept.add(candidate);
      } else {
        rejected.add(new RejectedFinding(candidate, reason));
      }
    }

    kept.sort(Comparator.comparing(Finding::filePath).thenComparingInt(Finding::line));
    return new ValidationOutcome(kept, rejected);
  }

  /** @return null 表示保留，非 null 表示拒绝理由 */
  private FindingRejectReason reasonFor(List<DiffFile> files, Finding finding) {
    if (finding.severity() == null
        || finding.message() == null
        || finding.message().isBlank()) {
      return FindingRejectReason.MISSING_FIELD;
    }

    DiffFile file = findFile(files, finding.filePath());
    if (file == null) {
      return FindingRejectReason.FILE_NOT_IN_DIFF;
    }

    if (finding.line() == 0) {
      return FindingRejectReason.ZERO_LINE;
    }

    Set<Integer> changedNewLines = new HashSet<>();
    Set<Integer> allNewLines = new HashSet<>();
    for (Hunk hunk : file.hunks()) {
      for (AnnotatedLine line : hunk.annotated()) {
        int newLine = line.newLine();
        if (newLine <= 0) {
          continue;
        }
        allNewLines.add(newLine);
        if (line.line().type() == LineType.ADDED) {
          changedNewLines.add(newLine);
        }
      }
    }

    if (changedNewLines.contains(finding.line())) {
      return null;
    }
    if (allNewLines.contains(finding.line())) {
      return FindingRejectReason.NOT_CHANGED_LINE;
    }
    return FindingRejectReason.NO_SUCH_NEW_LINE;
  }

  /** 只认 newPath 的精确字符串相等；删除文件（newPath == null）永不命中。 */
  private DiffFile findFile(List<DiffFile> files, String filePath) {
    for (DiffFile file : files) {
      if (file.newPath() != null && file.newPath().equals(filePath)) {
        return file;
      }
    }
    return null;
  }
}
