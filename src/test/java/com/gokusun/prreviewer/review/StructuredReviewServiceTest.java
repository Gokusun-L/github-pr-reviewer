package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gokusun.prreviewer.diff.AnnotatedLine;
import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.Hunk;
import com.gokusun.prreviewer.diff.LineType;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredReviewServiceTest {

  private static final String PR1_PATH = "src/main/java/demo/Calculator.java";
  private static final List<Duration> BACKOFF =
      List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));
  private static final String GARBAGE = "抱歉，我无法完成这次审查。";

  @Test
  void firstAttemptSuccessUsesOneCallAndNoSleep() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    StubModel model = new StubModel(twoFindingsJson(files));
    RecordingSleeper sleeper = new RecordingSleeper();

    ReviewResult result = service(model, sleeper).review(files);

    assertEquals(1, result.attempts());
    assertEquals(1, model.calls, "attempts 必须等于真实请求数");
    assertEquals(2, result.findings().size());
    assertTrue(sleeper.slept.isEmpty(), "首次就成功，不该退避");
  }

  @Test
  void retriesUntilThirdAttemptWithBackoff() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    StubModel model = new StubModel(GARBAGE, GARBAGE, twoFindingsJson(files));
    RecordingSleeper sleeper = new RecordingSleeper();

    ReviewResult result = service(model, sleeper).review(files);

    assertEquals(3, result.attempts());
    assertEquals(3, model.calls);
    assertEquals(2, result.findings().size());
    assertEquals(BACKOFF, sleeper.slept);
  }

  @Test
  void exhaustedAttemptsThrowParseFailed() throws IOException {
    StubModel model = new StubModel(GARBAGE);

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, new RecordingSleeper()).review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.PARSE_FAILED, thrown.kind());
    assertEquals(3, thrown.attempts());
    assertEquals(3, model.calls);
  }

  @Test
  void callFailureIsClassifiedAsCallFailed() throws IOException {
    StubModel model = new StubModel(new RuntimeException("connection reset"));

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, new RecordingSleeper()).review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(3, thrown.attempts(), "调用失败同样要按 3 次计");
    assertNull(thrown.rawSnippet(), "调用失败时没有原话可留");
  }

  @Test
  void nothingToReviewSkipsTheModelEntirely() throws IOException {
    StubModel model = new StubModel(GARBAGE);

    ReviewResult result = service(model, new RecordingSleeper()).review(parse("binary.diff"));

    assertEquals(0, result.attempts());
    assertEquals(0, model.calls, "没有可审文件就不该发请求");
    assertTrue(result.findings().isEmpty());
  }

  @Test
  void invalidFindingGoesToRejectedWithoutLosingTheValidOne() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    int addedLine = addedNewLines(files, 1).get(0);
    String json =
        """
        {"findings":[
          {"filePath":"%s","line":%d,"severity":"MAJOR","message":"m1"},
          {"filePath":"%s","line":999,"severity":"MINOR","message":"m2"}
        ]}
        """
            .formatted(PR1_PATH, addedLine, PR1_PATH);

    ReviewResult result = service(new StubModel(json), new RecordingSleeper()).review(files);

    assertEquals(1, result.findings().size());
    assertEquals(1, result.rejected().size());
    assertEquals(FindingRejectReason.NO_SUCH_NEW_LINE, result.rejected().get(0).reason());
  }

  @Test
  void systemPromptCarriesTheGeneratedSchema() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    StubModel model = new StubModel(twoFindingsJson(files));

    service(model, new RecordingSleeper()).review(files);

    assertTrue(model.systemPrompt.contains("findings"), "系统消息里必须有 schema 的字段名");
    assertTrue(model.systemPrompt.contains("BLOCKER"), "系统消息里必须有合法 severity 取值");
  }

  @Test
  void constructorRejectsWrongBackoffSize() {
    StubModel model = new StubModel(GARBAGE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StructuredReviewService(
                model,
                ReviewJson.converter(),
                new ReviewPromptBuilder(20_000),
                new FindingValidator(),
                List.of(Duration.ofSeconds(1)),
                new RecordingSleeper()));
  }

  private static StructuredReviewService service(
      ReviewModel model, StructuredReviewService.Sleeper sleeper) {
    return new StructuredReviewService(
        model,
        ReviewJson.converter(),
        new ReviewPromptBuilder(20_000),
        new FindingValidator(),
        BACKOFF,
        sleeper);
  }

  private static String twoFindingsJson(List<DiffFile> files) {
    List<Integer> lines = addedNewLines(files, 2);
    return """
        {"findings":[
          {"filePath":"%s","line":%d,"severity":"MAJOR","message":"m1"},
          {"filePath":"%s","line":%d,"severity":"MINOR","message":"m2"}
        ]}
        """
        .formatted(PR1_PATH, lines.get(0), PR1_PATH, lines.get(1));
  }

  private static List<Integer> addedNewLines(List<DiffFile> files, int howMany) {
    List<Integer> lines = new ArrayList<>();
    for (DiffFile file : files) {
      for (Hunk hunk : file.hunks()) {
        for (AnnotatedLine line : hunk.annotated()) {
          if (line.line().type() == LineType.ADDED && line.newLine() > 0) {
            lines.add(line.newLine());
          }
        }
      }
    }
    if (lines.size() < howMany) {
      throw new AssertionError("样本里 ADDED 行不足 " + howMany + " 条");
    }
    return lines;
  }

  private static List<DiffFile> parse(String name) throws IOException {
    try (InputStream in = StructuredReviewServiceTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new UnifiedDiffParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static final class StubModel implements ReviewModel {
    private final List<Object> responses;
    private int index;
    int calls;
    String systemPrompt;
    String userPrompt;

    StubModel(Object... responses) {
      this.responses = List.of(responses);
    }

    @Override
    public String invoke(String systemPrompt, String userPrompt) {
      calls++;
      this.systemPrompt = systemPrompt;
      this.userPrompt = userPrompt;
      Object next = responses.get(Math.min(index, responses.size() - 1));
      index++;
      if (next instanceof RuntimeException e) {
        throw e;
      }
      return (String) next;
    }
  }

  private static final class RecordingSleeper implements StructuredReviewService.Sleeper {
    private final List<Duration> slept = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
      slept.add(duration);
    }
  }
}
