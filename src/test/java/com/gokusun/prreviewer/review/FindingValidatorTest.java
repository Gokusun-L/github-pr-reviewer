package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class FindingValidatorTest {

  private static final FindingValidator VALIDATOR = new FindingValidator();

  /** pr1.diff 的真实新路径 —— 不是 displayName() 打印的那个短名。 */
  private static final String PR1_PATH = "src/main/java/demo/Calculator.java";

  @Test
  void changedLineIsKept() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    int addedLine = firstNewLineOf(files, LineType.ADDED);

    ValidationOutcome outcome =
        VALIDATOR.validate(files, List.of(finding(PR1_PATH, addedLine)));

    assertEquals(1, outcome.kept().size());
    assertEquals(addedLine, outcome.kept().get(0).line());
    assertTrue(outcome.rejected().isEmpty());
  }

  @Test
  void contextLineIsRejectedAsNotChanged() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    int contextLine = firstNewLineOf(files, LineType.CONTEXT);

    ValidationOutcome outcome =
        VALIDATOR.validate(files, List.of(finding(PR1_PATH, contextLine)));

    assertTrue(outcome.kept().isEmpty());
    assertEquals(FindingRejectReason.NOT_CHANGED_LINE, outcome.rejected().get(0).reason());
  }

  @Test
  void zeroLineIsItsOwnReason() throws IOException {
    List<DiffFile> files = parse("pr1.diff");

    ValidationOutcome outcome = VALIDATOR.validate(files, List.of(finding(PR1_PATH, 0)));

    assertEquals(FindingRejectReason.ZERO_LINE, outcome.rejected().get(0).reason());
  }

  @Test
  void absentLineIsRejectedAsNoSuchNewLine() throws IOException {
    List<DiffFile> files = parse("pr1.diff");

    ValidationOutcome outcome = VALIDATOR.validate(files, List.of(finding(PR1_PATH, 999)));

    assertEquals(FindingRejectReason.NO_SUCH_NEW_LINE, outcome.rejected().get(0).reason());
  }

  @Test
  void deletedFileNeverMatchesEvenByItsDisplayName() throws IOException {
    List<DiffFile> files = parse("delete-empty.diff");
    // displayName() 会返回 "empty.txt"，但 newPath 是 null —— 用显示名就会被误判为"找到了"
    assertEquals("empty.txt", files.get(0).displayName());

    ValidationOutcome outcome = VALIDATOR.validate(files, List.of(finding("empty.txt", 1)));

    assertEquals(FindingRejectReason.FILE_NOT_IN_DIFF, outcome.rejected().get(0).reason());
  }

  @Test
  void missingSeverityIsRejectedInsteadOfLeakingNull() {
    Finding noSeverity = new Finding(PR1_PATH, 1, null, "msg", null);

    ValidationOutcome outcome = VALIDATOR.validate(List.of(), List.of(noSeverity));

    assertEquals(FindingRejectReason.MISSING_FIELD, outcome.rejected().get(0).reason());
  }

  @Test
  void missingLineIsRejectedAsMissingField() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    var converter = ReviewJson.converter();

    LlmReview missingLine =
        converter.convert(
            "{\"findings\":[{\"filePath\":\"%s\",\"severity\":\"MINOR\",\"message\":\"m\"}]}"
                .formatted(PR1_PATH));
    assertEquals(
        FindingRejectReason.MISSING_FIELD,
        VALIDATOR.validate(files, missingLine.findings()).rejected().get(0).reason());

    LlmReview zeroLine =
        converter.convert(
            "{\"findings\":[{\"filePath\":\"%s\",\"line\":0,\"severity\":\"MINOR\",\"message\":\"m\"}]}"
                .formatted(PR1_PATH));
    assertEquals(
        FindingRejectReason.ZERO_LINE,
        VALIDATOR.validate(files, zeroLine.findings()).rejected().get(0).reason());
  }

  @Test
  void blankMessageIsRejectedAsMissingField() {
    Finding blankMessage = new Finding(PR1_PATH, 1, Severity.MINOR, "   ", null);

    ValidationOutcome outcome = VALIDATOR.validate(List.of(), List.of(blankMessage));

    assertEquals(FindingRejectReason.MISSING_FIELD, outcome.rejected().get(0).reason());
  }

  @Test
  void keptIsSortedByFilePathThenLine() throws IOException {
    List<DiffFile> files = parse("pr1.diff");
    int first = firstNewLineOf(files, LineType.ADDED);
    int last = lastNewLineOf(files, LineType.ADDED);

    ValidationOutcome outcome =
        VALIDATOR.validate(
            files,
            List.of(finding(PR1_PATH, last), finding(PR1_PATH, first)));

    assertEquals(2, outcome.kept().size());
    assertEquals(first, outcome.kept().get(0).line());
    assertEquals(last, outcome.kept().get(1).line());
  }

  @Test
  void rejectedKeepsModelOrder() throws IOException {
    List<DiffFile> files = parse("pr1.diff");

    ValidationOutcome outcome =
        VALIDATOR.validate(
            files, List.of(finding("nope.java", 1), finding(PR1_PATH, 0)));

    assertEquals(FindingRejectReason.FILE_NOT_IN_DIFF, outcome.rejected().get(0).reason());
    assertEquals(FindingRejectReason.ZERO_LINE, outcome.rejected().get(1).reason());
  }

  @Test
  void rejectedFindingRequiresAReason() {
    Finding finding = finding(PR1_PATH, 1);

    assertThrows(NullPointerException.class, () -> new RejectedFinding(finding, null));
  }

  private static Finding finding(String filePath, int line) {
    return new Finding(filePath, line, Severity.MINOR, "msg", null);
  }

  private static List<DiffFile> parse(String name) throws IOException {
    try (InputStream in = FindingValidatorTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new UnifiedDiffParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static int firstNewLineOf(List<DiffFile> files, LineType type) {
    for (DiffFile file : files) {
      for (Hunk hunk : file.hunks()) {
        for (AnnotatedLine line : hunk.annotated()) {
          if (line.line().type() == type && line.newLine() > 0) {
            return line.newLine();
          }
        }
      }
    }
    throw new AssertionError("样本里没有 " + type + " 行");
  }

  private static int lastNewLineOf(List<DiffFile> files, LineType type) {
    int result = -1;
    for (DiffFile file : files) {
      for (Hunk hunk : file.hunks()) {
        for (AnnotatedLine line : hunk.annotated()) {
          if (line.line().type() == type && line.newLine() > 0) {
            result = line.newLine();
          }
        }
      }
    }
    if (result < 0) {
      throw new AssertionError("样本里没有 " + type + " 行");
    }
    return result;
  }
}
