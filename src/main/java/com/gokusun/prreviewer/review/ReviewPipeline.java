package com.gokusun.prreviewer.review;

import com.gokusun.prreviewer.diff.ParseFailure;
import com.gokusun.prreviewer.diff.ParseReport;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.util.List;
import java.util.Objects;

/**
 * 取 diff、解析、送审的编排。
 *
 * <p>不依赖 Spring，diff 来源降成一个函数接口，测试可直接喂固定文本。
 */
public final class ReviewPipeline {

  /** 把"去哪拿 diff"降成一个函数；运行时传方法引用，测试传 lambda。 */
  @FunctionalInterface
  public interface DiffSource {
    String fetch(String owner, String repo, int pr);
  }

  private final DiffSource diffSource;
  private final UnifiedDiffParser parser;
  private final StructuredReviewService reviewService;

  public ReviewPipeline(
      DiffSource diffSource,
      UnifiedDiffParser parser,
      StructuredReviewService reviewService) {
    this.diffSource = Objects.requireNonNull(diffSource, "diffSource");
    this.parser = Objects.requireNonNull(parser, "parser");
    this.reviewService = Objects.requireNonNull(reviewService, "reviewService");
  }

  public PipelineResult run(String owner, String repo, int pr) {
    String diff = diffSource.fetch(owner, repo, pr);
    if (diff == null || diff.isBlank()) {
      throw new IllegalStateException("拉到的 diff 为空：" + owner + "/" + repo + " #" + pr);
    }
    ParseReport report = parser.parseReport(diff);
    if (report.files().isEmpty()) {
      throw new IllegalStateException("没有可解析的文件：" + describe(report.failures()));
    }
    ReviewResult review = reviewService.review(report.files());
    return new PipelineResult(review, report.failures());
  }

  private static String describe(List<ParseFailure> failures) {
    if (failures.isEmpty()) {
      return "diff 里没有 diff --git 块";
    }
    StringBuilder text = new StringBuilder("全部文件解析失败");
    for (ParseFailure failure : failures) {
      text.append("\n  ").append(failure.path()).append(": ").append(failure.reason());
    }
    return text.toString();
  }
}
