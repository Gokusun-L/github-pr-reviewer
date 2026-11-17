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
import org.springframework.web.client.RestClientResponseException;

/** 单次审查的命令行入口。 */
@Component
public class ReviewCli implements ApplicationRunner {

  /** 审查成功；发现了问题也算成功。 */
  static final int CODE_OK = 0;

  /** 审查没做成：模型调用或解析失败。 */
  static final int CODE_REVIEW_FAILED = 1;

  /** 输入或环境错误：参数非法、token 缺失、远端 4xx。 */
  static final int CODE_INPUT_ERROR = 2;

  private final ReviewPipeline pipeline;

  public ReviewCli(ReviewPipeline pipeline) {
    this.pipeline = pipeline;
  }

  /** 执行审查；退出码非零时结束进程。 */
  @Override
  public void run(ApplicationArguments args) {
    int code = execute(args);
    if (code != CODE_OK) {
      System.exit(code);
    }
  }

  /**
   * 执行一次审查并返回退出码。
   *
   * @param args 启动参数；从 --owner / --repo / --pr 读取，缺省为 Gokusun-L / github-pr-reviewer / 1
   * @return 0 成功；1 审查失败；2 输入或环境错误
   */
  int execute(ApplicationArguments args) {
    try {
      String owner = option(args, "owner", "Gokusun-L");
      String repo = option(args, "repo", "github-pr-reviewer");
      int pr = parsePr(option(args, "pr", "1"));
      print(pipeline.run(owner, repo, pr));
      return CODE_OK;
    } catch (IllegalArgumentException e) {
      System.out.println("INPUT_ERROR " + e.getMessage());
      return CODE_INPUT_ERROR;
    } catch (IllegalStateException e) {
      System.out.println("INPUT_ERROR " + e.getMessage());
      return CODE_INPUT_ERROR;
    } catch (RestClientResponseException e) {
      System.out.println(
          "INPUT_ERROR status=" + e.getStatusCode().value() + " " + e.getStatusText());
      return CODE_INPUT_ERROR;
    } catch (ReviewFailedException e) {
      System.out.println(
          "REVIEW_FAILED kind=" + e.kind() + " attempts=" + e.attempts() + " " + e.getMessage());
      if (e.rawSnippet() != null && !e.rawSnippet().isBlank()) {
        System.out.println("RAW " + e.rawSnippet());
      }
      return CODE_REVIEW_FAILED;
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
