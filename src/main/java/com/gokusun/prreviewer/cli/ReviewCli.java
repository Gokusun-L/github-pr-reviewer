package com.gokusun.prreviewer.cli;

import com.gokusun.prreviewer.diff.ParseFailure;
import com.gokusun.prreviewer.review.Finding;
import com.gokusun.prreviewer.review.PipelineResult;
import com.gokusun.prreviewer.review.RejectedFinding;
import com.gokusun.prreviewer.review.ReviewFailedException;
import com.gokusun.prreviewer.review.ReviewPipeline;
import com.gokusun.prreviewer.review.ReviewResult;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 单次审查的命令行入口。 */
@Component
public class ReviewCli implements ApplicationRunner {

  private final ReviewPipeline pipeline;

  public ReviewCli(ReviewPipeline pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public void run(ApplicationArguments args) {
    String owner = option(args, "owner", "Gokusun-L");
    String repo = option(args, "repo", "github-pr-reviewer");
    int pr = parsePr(option(args, "pr", "1"));

    try {
      print(pipeline.run(owner, repo, pr));
    } catch (ReviewFailedException e) {
      System.out.println(
          "REVIEW_FAILED kind=" + e.kind() + " attempts=" + e.attempts() + " " + e.getMessage());
      if (e.rawSnippet() != null && !e.rawSnippet().isBlank()) {
        System.out.println("RAW " + e.rawSnippet());
      }
    } catch (IllegalStateException e) {
      System.out.println("INPUT_ERROR " + e.getMessage());
    }
  }

  private static void print(PipelineResult result) {
    ReviewResult review = result.review();

    System.out.println("FINDINGS " + review.findings().size());
    for (Finding finding : review.findings()) {
      System.out.println(
          "  "
              + finding.filePath()
              + ":"
              + finding.line()
              + " ["
              + finding.severity()
              + "] "
              + finding.message());
    }

    System.out.println("REJECTED " + review.rejected().size());
    for (RejectedFinding rejected : review.rejected()) {
      Finding finding = rejected.finding();
      System.out.println("  " + finding.filePath() + ":" + finding.line() + " " + rejected.reason());
    }

    System.out.println("SKIPPED " + review.skippedFiles().size());
    for (String path : review.skippedFiles()) {
      System.out.println("  " + path);
    }

    System.out.println("FAILED " + result.failedFiles().size());
    for (ParseFailure failure : result.failedFiles()) {
      System.out.println("  " + failure.path() + ": " + failure.reason());
    }

    System.out.println("attempts=" + review.attempts());
  }

  /** 取 --name=value 的值；该选项未出现时返回 fallback。 */
  private static String option(ApplicationArguments args, String name, String fallback) {
    List<String> values = args.getOptionValues(name);
    return values == null ? fallback : values.get(0);
  }

  /** 把 --pr 的值转成整数；非数字时抛出 IllegalArgumentException。 */
  private static int parsePr(String raw) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("--pr 不是数字: " + raw, e);
    }
  }
}
