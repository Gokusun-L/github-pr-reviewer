package com.gokusun.prreviewer.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewPipelineTest {

  private static final Path PR1 = Path.of("src/test/resources/diffs/pr1.diff");
  private static final String PR1_FILE = "src/main/java/demo/Calculator.java";

  @Test
  void runReturnsFindingsAtRealNewSideLineNumbers() throws IOException {
    String diff = Files.readString(PR1, StandardCharsets.UTF_8);
    String json =
        "{\"findings\":[{\"filePath\":\""
            + PR1_FILE
            + "\",\"line\":5,\"severity\":\"MAJOR\","
            + "\"message\":\"硬编码密钥\",\"suggestion\":\"改为环境变量\"}]}";

    PipelineResult result = pipelineFor(diff, json).run("Gokusun-L", "github-pr-reviewer", 1);

    assertThat(result.review().findings()).hasSize(1);
    Finding finding = result.review().findings().get(0);
    assertThat(finding.filePath()).isEqualTo(PR1_FILE);
    assertThat(finding.line()).isEqualTo(5);
    assertThat(finding.severity()).isEqualTo(Severity.MAJOR);
    assertThat(result.review().attempts()).isEqualTo(1);
    assertThat(result.failedFiles()).isEmpty();
  }

  @Test
  void runKeepsGoodFilesWhenOneFileIsBroken() {
    String good =
        "diff --git a/A.java b/A.java\n"
            + "index 1111111..2222222 100644\n"
            + "--- a/A.java\n"
            + "+++ b/A.java\n"
            + "@@ -1,1 +1,2 @@\n"
            + " class A {}\n"
            + "+// x\n";
    String broken =
        "diff --git a/B.java b/B.java\n"
            + "index 3333333..4444444 100644\n"
            + "--- a/B.java\n"
            + "+++ b/B.java\n"
            + "@@ -1,99 +1,99 @@\n"
            + "-a\n"
            + "+b\n";

    PipelineResult result = pipelineFor(good + broken, "{\"findings\":[]}").run("o", "r", 1);

    assertThat(result.review().findings()).isEmpty();
    assertThat(result.failedFiles()).hasSize(1);
    assertThat(result.failedFiles().get(0).path()).isEqualTo("B.java");
  }

  @Test
  void blankDiffIsRejected() {
    assertThatThrownBy(() -> pipelineFor("   ", "{\"findings\":[]}").run("o", "r", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("拉到的 diff 为空");
  }

  @Test
  void nonGitTextIsRejected() {
    assertThatThrownBy(() -> pipelineFor("hello world", "{\"findings\":[]}").run("o", "r", 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("没有可解析的文件");
  }

  private static ReviewPipeline pipelineFor(String diff, String modelJson) {
    ReviewModel model = (systemPrompt, userPrompt) -> modelJson;
    StructuredReviewService service =
        new StructuredReviewService(
            model,
            ReviewJson.converter(),
            new ReviewPromptBuilder(20_000),
            new FindingValidator(),
            List.of(Duration.ZERO, Duration.ZERO),
            duration -> {});
    return new ReviewPipeline((owner, repo, pr) -> diff, new UnifiedDiffParser(), service);
  }
}
