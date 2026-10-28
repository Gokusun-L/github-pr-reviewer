package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnifiedDiffParserTest {

  @Test
  void pr1_parsesSingleFileSingleHunk() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("pr1.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.MODIFIED, files.get(0).kind());
    assertEquals(1, files.get(0).hunks().size());

    Hunk hunk = files.get(0).hunks().get(0);
    assertEquals(1, hunk.oldStart());
    assertEquals(8, hunk.oldCount());
    assertEquals(1, hunk.newStart());
    assertEquals(15, hunk.newCount());
    assertEquals(1, count(hunk, LineType.CONTEXT));
    assertEquals(14, count(hunk, LineType.ADDED));
    assertEquals(7, count(hunk, LineType.REMOVED));
    assertEquals(1, count(hunk, LineType.NO_NEWLINE));
  }

  @Test
  void multiFile_parsesThreeFilesAndTwoNewOnes() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));

    assertEquals(3, files.size());
    assertEquals(FileKind.ADDED, files.get(1).kind());
    assertNull(files.get(1).oldPath());
    assertEquals(0, files.get(1).hunks().get(0).oldCount());
    assertEquals(FileKind.ADDED, files.get(2).kind());
    assertEquals(0, files.get(2).hunks().get(0).oldCount());
  }

  @Test
  void githubApiShapeCrlfAndLocalLfBothResolvePaths() throws IOException {
    String mixedText = read("pr1.diff");
    String lfText = read("multi-file.diff");

    assertEquals(14, countCr(mixedText), "pr1.diff 必须恰好 14 个 CR");
    assertEquals(0, countCr(lfText), "multi-file.diff 必须是纯 LF");

    List<DiffFile> mixedFiles = new UnifiedDiffParser().parse(mixedText);
    List<DiffFile> lfFiles = new UnifiedDiffParser().parse(lfText);

    assertEquals("src/main/java/demo/Calculator.java", mixedFiles.get(0).newPath());
    assertEquals(
        "src/main/java/com/gokusun/prreviewer/PrReviewerApplication.java",
        lfFiles.get(0).newPath());
  }

  @Test
  void pureCrlfDiffParsesSameAsLfDiff() throws IOException {
    String lfText = read("multi-file.diff");
    String pureCrlf = lfText.replace("\n", "\r\n");

    assertEquals(0, countBareLf(pureCrlf), "派生样本不得残留裸 LF");

    List<DiffFile> lfFiles = new UnifiedDiffParser().parse(lfText);
    List<DiffFile> crlfFiles = new UnifiedDiffParser().parse(pureCrlf);

    assertEquals(lfFiles.size(), crlfFiles.size());
    for (int i = 0; i < lfFiles.size(); i++) {
      DiffFile left = lfFiles.get(i);
      DiffFile right = crlfFiles.get(i);
      assertEquals(left.oldPath(), right.oldPath(), "第 " + i + " 个文件 oldPath 不一致");
      assertEquals(left.newPath(), right.newPath(), "第 " + i + " 个文件 newPath 不一致");
      assertEquals(left.kind(), right.kind(), "第 " + i + " 个文件 kind 不一致");
      assertEquals(left.hunks().size(), right.hunks().size(), "第 " + i + " 个文件 hunk 数不一致");

      for (int j = 0; j < left.hunks().size(); j++) {
        Hunk leftHunk = left.hunks().get(j);
        Hunk rightHunk = right.hunks().get(j);
        assertEquals(leftHunk.oldStart(), rightHunk.oldStart());
        assertEquals(leftHunk.oldCount(), rightHunk.oldCount());
        assertEquals(leftHunk.newStart(), rightHunk.newStart());
        assertEquals(leftHunk.newCount(), rightHunk.newCount());
        assertEquals(
            leftHunk.lines().size(), rightHunk.lines().size(), "行数不一致：\\r 被当成了内容");
        for (DiffLine line : rightHunk.lines()) {
          assertFalse(line.content().endsWith("\r"), "纯 CRLF 样本里 \\r 不得残留在内容里");
        }
      }
    }
  }

  @Test
  void modeOnlyChangeKeepsBothPathsWithoutHunks() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("mode-only.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.MODIFIED, files.get(0).kind());
    assertEquals("script.sh", files.get(0).oldPath());
    assertEquals("script.sh", files.get(0).newPath());
    assertTrue(files.get(0).hunks().isEmpty());
  }

  @Test
  void deletedEmptyFileHasNullNewPath() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("delete-empty.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.DELETED, files.get(0).kind());
    assertEquals("empty.txt", files.get(0).oldPath());
    assertNull(files.get(0).newPath());
  }

  @Test
  void addedEmptyFileHasNullOrldPath() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("add-empty.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.ADDED, files.get(0).kind());
    assertNull(files.get(0).oldPath());
    assertEquals("empty.txt", files.get(0).newPath());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "binary.diff",
        "rename-only.diff",
        "mode-only.diff",
        "delete-empty.diff",
        "add-empty.diff",
        "quoted-path.diff"
      })
  void displayNameIsNeverNullOrBlank(String sample) throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read(sample));

    for (DiffFile file : files) {
      String name = file.displayName();
      assertNotNull(name, sample + " 里出现了 displayName() == null");
      assertFalse(name.isBlank(), sample + " 里出现了空白 displayName");
    }
  }

  @Test
  void truncatedDiffHeaderStillYieldsDisplayName() {
    List<DiffFile> files = new UnifiedDiffParser().parse("diff --git ");

    assertEquals(1, files.size());
    assertNotNull(files.get(0).displayName());
    assertFalse(files.get(0).displayName().isBlank());
  }

  @Test
  void binaryFileKeepsPathAndHasNoHunks() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("binary.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.ADDED, files.get(0).kind());
    assertEquals("logo.png", files.get(0).newPath());
    assertTrue(files.get(0).hunks().isEmpty());
  }

  @Test
  void quotedPathIsDecoded() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("quoted-path.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.ADDED, files.get(0).kind());
    assertNull(files.get(0).oldPath());
    assertEquals("中文.java", files.get(0).newPath());
    assertEquals(1, count(files.get(0).hunks().get(0), LineType.ADDED));
  }

  @Test
  void renameOnlyProducesRenamedWithBothPaths() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("rename-only.diff"));

    assertEquals(1, files.size());
    assertEquals(FileKind.RENAMED, files.get(0).kind());
    assertEquals("keep.txt", files.get(0).oldPath());
    assertEquals("renamed.txt", files.get(0).newPath());
    assertTrue(files.get(0).hunks().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pr1.diff",
        "multi-file.diff",
        "binary.diff",
        "quoted-path.diff",
        "rename-only.diff",
        "mode-only.diff",
        "delete-empty.diff",
        "add-empty.diff"
      })
  void everySampleParsesWithoutException(String sample) throws IOException {
    String text = read(sample);
    assertDoesNotThrow(() -> new UnifiedDiffParser().parse(text));
  }

  private static int countCr(String text) {
    return (int) text.chars().filter(c -> c == '\r').count();
  }

  private static int countBareLf(String text) {
    return (int) text.replace("\r\n", "").chars().filter(c -> c == '\n').count();
  }

  private static long count(Hunk hunk, LineType type) {
    return hunk.lines().stream().filter(line -> line.type() == type).count();
  }

  private static String read(String name) throws IOException {
    try (InputStream in = UnifiedDiffParserTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
