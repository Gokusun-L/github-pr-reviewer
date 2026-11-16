package com.gokusun.prreviewer.review;

import com.gokusun.prreviewer.diff.ParseFailure;
import java.util.List;
import java.util.Objects;

/**
 * 一次编排的完整产出：审查结果 + 解析阶段被丢掉的文件。
 *
 * <p>失败文件不并入 {@link ReviewResult#skippedFiles()}：后者是"预算裁剪掉的"，
 * 这里是"解析拿不到的"，两者的处置动作不同。
 */
public record PipelineResult(ReviewResult review, List<ParseFailure> failedFiles) {

  public PipelineResult {
    Objects.requireNonNull(review, "review");
    failedFiles = List.copyOf(failedFiles);
  }
}
