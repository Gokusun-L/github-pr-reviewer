package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReviewPromptBuilderTest {

  private static final int UNLIMITED = Integer.MAX_VALUE;

  @Test
  void promptContainsNoCarriageReturn() throws IOException {
    BuiltPrompt prompt = new ReviewPromptBuilder(UNLIMITED).build(parse("pr1.diff"));

    assertFalse(prompt.userPrompt().contains("\r"), "prompt 里出现 \\r 会把行粘在一起");
  }

  @Test
  void addedAndContextLinesCarryLineNumbersRemovedLinesDoNot() throws IOException {
    BuiltPrompt prompt = new ReviewPromptBuilder(UNLIMITED).build(parse("pr1.diff"));

    assertTrue(prompt.userPrompt().contains("### FILE src/main/java/demo/Calculator.java"));
    // `-` 行前面不允许出现数字
    assertFalse(Pattern.compile("(?m)^\\s*\\d+\\s+-").matcher(prompt.userPrompt()).find());
  }

  @Test
  void deletedFilesNeverReachThePrompt() throws IOException {
    BuiltPrompt prompt = new ReviewPromptBuilder(UNLIMITED).build(parse("delete-empty.diff"));

    assertFalse(prompt.userPrompt().contains("empty.txt"));
    assertTrue(prompt.nothingToReview());
    assertTrue(prompt.renderedFiles().isEmpty());
  }

  @Test
  void binaryFilesAreNotReviewable() throws IOException {
    BuiltPrompt prompt = new ReviewPromptBuilder(UNLIMITED).build(parse("binary.diff"));

    assertTrue(prompt.nothingToReview());
    assertTrue(prompt.renderedFiles().isEmpty());
    assertTrue(prompt.skippedFiles().isEmpty(), "不可审文件不算被预算跳过");
  }

  @Test
  void promptIsIdempotent() throws IOException {
    ReviewPromptBuilder builder = new ReviewPromptBuilder(UNLIMITED);
    List<DiffFile> files = parse("pr1.diff");

    assertEquals(builder.build(files).userPrompt(), builder.build(files).userPrompt());
  }

  @Test
  void budgetKeepsWholeFilesAndAccountsForBothOutcomes() throws IOException {
    List<DiffFile> files = parse("multi-file.diff");
    List<DiffFile> reviewable = reviewableOf(files);
    int firstBlockChars =
        new ReviewPromptBuilder(UNLIMITED).build(List.of(reviewable.get(0))).userPrompt().length();

    BuiltPrompt prompt = new ReviewPromptBuilder(firstBlockChars).build(files);

    assertTrue(prompt.userPrompt().length() <= firstBlockChars);
    assertEquals(1, prompt.renderedFiles().size(), "预算只够第一个文件");
    assertEquals(
        reviewable.size(),
        prompt.renderedFiles().size() + prompt.skippedFiles().size(),
        "可审文件只有两个去处，没有第三个");
    assertTrue(prompt.truncated());
  }

  @Test
  void allFilesSkippedIsAFailureNotAnEmptyResult() throws IOException {
    List<DiffFile> files = parse("multi-file.diff");
    int firstBlockChars =
        new ReviewPromptBuilder(UNLIMITED)
            .build(List.of(reviewableOf(files).get(0)))
            .userPrompt()
            .length();

    ReviewFailedException thrown =
        assertThrows(
            ReviewFailedException.class,
            () -> new ReviewPromptBuilder(firstBlockChars - 1).build(files));

    assertEquals(ReviewFailureKind.BUDGET_EXHAUSTED, thrown.kind());
  }

  @Test
  void nonPositiveBudgetFailsAtConstruction() {
    assertThrows(IllegalArgumentException.class, () -> new ReviewPromptBuilder(0));
    assertThrows(IllegalArgumentException.class, () -> new ReviewPromptBuilder(-1));
  }

  @Test
  void failureRequiresKindAndNonNegativeAttempts() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReviewFailedException(null, 0, null, "x", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ReviewFailedException(ReviewFailureKind.CALL_FAILED, -1, null, "x", null));
  }

  private static List<DiffFile> reviewableOf(List<DiffFile> files) {
    List<DiffFile> reviewable = new ArrayList<>();
    for (DiffFile file : files) {
      if (file.newPath() != null && !file.hunks().isEmpty()) {
        reviewable.add(file);
      }
    }
    return reviewable;
  }

  private static List<DiffFile> parse(String name) throws IOException {
    try (InputStream in = ReviewPromptBuilderTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new UnifiedDiffParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
