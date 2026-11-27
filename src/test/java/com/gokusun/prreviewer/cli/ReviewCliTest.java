package com.gokusun.prreviewer.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import com.gokusun.prreviewer.github.CommentPublisher;
import com.gokusun.prreviewer.github.PublishReport;
import com.gokusun.prreviewer.review.FindingValidator;
import com.gokusun.prreviewer.review.ReviewFailedException;
import com.gokusun.prreviewer.review.ReviewFailureKind;
import com.gokusun.prreviewer.review.ReviewJson;
import com.gokusun.prreviewer.review.ReviewModel;
import com.gokusun.prreviewer.review.ReviewPipeline;
import com.gokusun.prreviewer.review.ReviewPromptBuilder;
import com.gokusun.prreviewer.review.ReviewResult;
import com.gokusun.prreviewer.review.StructuredReviewService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

class ReviewCliTest {

  private static final String MINIMAL_DIFF =
      "diff --git a/A.java b/A.java\n"
          + "index 1111111..2222222 100644\n"
          + "--- a/A.java\n"
          + "+++ b/A.java\n"
          + "@@ -1,1 +1,2 @@\n"
          + " class A {}\n"
          + "+// x\n";

  private static final CommentPublisher MUST_NOT_PUBLISH =
      new CommentPublisher(null) {
        @Override
        public PublishReport publish(String owner, String repo, int number, ReviewResult review) {
          throw new AssertionError("不带 --post 时不得发布评论");
        }
      };

  @Test
  void successReturnsZero() {
    ReviewCli cli = cliFor(MINIMAL_DIFF);

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    assertThat(code).isZero();
  }

  @Test
  void reviewFailedReturnsOne() {
    ReviewCli cli =
        cliThatThrows(
            new ReviewFailedException(ReviewFailureKind.CALL_FAILED, 3, null, "模型审查失败", null));

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    assertThat(code).isEqualTo(ReviewCli.CODE_REVIEW_FAILED);
  }

  @Test
  void nonNumericPrReturnsTwo() {
    ReviewCli cli = cliFor(MINIMAL_DIFF);

    int code = cli.execute(new DefaultApplicationArguments("--pr=abc"));

    assertThat(code).isEqualTo(ReviewCli.CODE_INPUT_ERROR);
  }

  @Test
  void illegalStateReturnsTwo() {
    ReviewCli cli = cliThatThrows(new IllegalStateException("github.token 未配置"));

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    assertThat(code).isEqualTo(ReviewCli.CODE_INPUT_ERROR);
  }

  @Test
  void githubClientErrorReturnsTwo() {
    HttpClientErrorException notFound =
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
            "Not Found",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);
    ReviewCli cli = cliThatThrows(notFound);

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    assertThat(code).isEqualTo(ReviewCli.CODE_INPUT_ERROR);
  }

  @Test
  void networkFailureReturnsTwo() {
    ReviewCli cli =
        cliThatThrows(
            new RestClientException(
                "Error while extracting response", new SocketException("Connection reset")));

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    assertThat(code).isEqualTo(ReviewCli.CODE_INPUT_ERROR);
  }

  @Test
  void withoutPostOptionPublisherIsNotCalled() {
    ReviewCli cli = cliFor(MINIMAL_DIFF);

    int code = cli.execute(new DefaultApplicationArguments("--pr=1"));

    // 未抛 AssertionError 即证明 publisher 一次都没被调用
    assertThat(code).isZero();
  }

  @Test
  void postOptionPublishesOnce() {
    List<ReviewResult> seen = new ArrayList<>();
    CommentPublisher recording =
        new CommentPublisher(null) {
          @Override
          public PublishReport publish(String owner, String repo, int number, ReviewResult review) {
            seen.add(review);
            return new PublishReport(1, List.of());
          }
        };
    ReviewCli cli = new ReviewCli(pipelineFor(MINIMAL_DIFF), recording);

    int code = cli.execute(new DefaultApplicationArguments("--pr=1", "--post"));

    assertThat(code).isZero();
    assertThat(seen).hasSize(1);
  }

  @Test
  void postReportPrintsNetworkForMissingStatusCode() {
    CommentPublisher fixed =
        new CommentPublisher(null) {
          @Override
          public PublishReport publish(String owner, String repo, int number, ReviewResult review) {
            return new PublishReport(
                0, List.of(new PublishReport.Failure("A.java", 1, null, true)));
          }
        };
    ReviewCli cli = new ReviewCli(pipelineFor(MINIMAL_DIFF), fixed);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream original = System.out;
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    int code;
    try {
      code = cli.execute(new DefaultApplicationArguments("--pr=1", "--post"));
    } finally {
      System.setOut(original);
    }

    assertThat(code).isZero();
    assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("A.java:1 status=NETWORK");
  }

  private static ReviewCli cliFor(String diff) {
    return new ReviewCli(pipelineFor(diff), MUST_NOT_PUBLISH);
  }

  private static ReviewCli cliThatThrows(RuntimeException failure) {
    return new ReviewCli(
        new ReviewPipeline(
            (owner, repo, pr) -> {
              throw failure;
            },
            new UnifiedDiffParser(),
            reviewService("{\"findings\":[]}")),
        MUST_NOT_PUBLISH);
  }

  private static ReviewPipeline pipelineFor(String diff) {
    return new ReviewPipeline(
        (owner, repo, pr) -> diff, new UnifiedDiffParser(), reviewService("{\"findings\":[]}"));
  }

  private static StructuredReviewService reviewService(String modelJson) {
    ReviewModel model = (systemPrompt, userPrompt) -> modelJson;
    return new StructuredReviewService(
        model,
        ReviewJson.converter(),
        new ReviewPromptBuilder(20_000),
        new FindingValidator(),
        List.of(Duration.ZERO, Duration.ZERO),
        duration -> true);
  }
}
