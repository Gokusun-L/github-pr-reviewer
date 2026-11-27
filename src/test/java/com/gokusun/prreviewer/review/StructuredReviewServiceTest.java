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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

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
    StubModel model = new StubModel(new ResourceAccessException("Connection reset"));

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, new RecordingSleeper()).review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(3, thrown.attempts(), "调用失败同样要按 3 次计");
    assertEquals(3, model.calls, "网络异常属于瞬时失败，要真实重试 3 次");
    assertNull(thrown.rawSnippet(), "调用失败时没有原话可留");
  }

  @Test
  void nonTransientFailureIsNotRetried() throws IOException {
    StubModel model = new StubModel(new NonTransientAiException("401 - bad key"));

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, new RecordingSleeper()).review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(1, thrown.attempts(), "确定性失败第一次就该停");
    assertEquals(1, model.calls);
  }

  @Test
  void transientFailureIsRetriedThreeTimes() throws IOException {
    StubModel model = new StubModel(new TransientAiException("503 - upstream"));

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, new RecordingSleeper()).review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(3, thrown.attempts(), "瞬时失败要按 3 次计");
    assertEquals(3, model.calls);
  }

  @Test
  void interruptedBackoffReportsAttemptsAlreadyRan() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    StubModel model = new StubModel(new TransientAiException("503 - upstream"));
    AtomicInteger sleeps = new AtomicInteger();
    StructuredReviewService.Sleeper sleeper = duration -> sleeps.incrementAndGet() < 2;

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service(model, sleeper).review(files));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(2, thrown.attempts(), "中断发生在第 2 次退避前，此前已完成 2 轮");
    assertEquals(2, model.calls);
    assertNull(thrown.rawSnippet(), "等待被中断时没有模型原话");
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
    public boolean sleep(Duration duration) {
      slept.add(duration);
      return true;
    }
  }
}
