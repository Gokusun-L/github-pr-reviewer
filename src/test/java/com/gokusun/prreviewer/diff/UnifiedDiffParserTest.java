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
import java.util.ArrayList;
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
    assertEquals(22, hunk.lines().size());
  }

  @Test
  void noNewlineMarkerFoldsIntoPrecedingLine() throws IOException {
    Hunk hunk = new UnifiedDiffParser().parse(read("pr1.diff")).get(0).hunks().get(0);

    assertEquals(22, hunk.lines().size(), "标记行不得占用 lines 的一格");
    assertEquals(14, count(hunk, LineType.ADDED));
    assertEquals(7, count(hunk, LineType.REMOVED));
    assertEquals(1, count(hunk, LineType.CONTEXT));
    assertTrue(hunk.lines().get(21).noNewline(), "\\ No newline 应折叠为上一行的标志");
    assertFalse(hunk.lines().get(20).noNewline(), "不得折叠到更前面的行");
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
  void crlfTerminatedDiffTextKeepsStructure() throws IOException {
    // CRLF 分隔的 diff 文本不在产品输入契约内（API 用 \n 分行）。这里只保证它不崩、
    // 不把 \r 当成新行。path 与 kind 在契约外输入上会漂移（见交付单），因此不断言。
    String lfText = read("multi-file.diff");
    String pureCrlf = lfText.replace("\n", "\r\n");

    assertEquals(0, countBareLf(pureCrlf), "派生样本不得残留裸 LF");

    List<DiffFile> lfFiles = new UnifiedDiffParser().parse(lfText);
    List<DiffFile> crlfFiles = new UnifiedDiffParser().parse(pureCrlf);

    assertEquals(lfFiles.size(), crlfFiles.size(), "文件数不一致");
    for (int i = 0; i < lfFiles.size(); i++) {
      Hunk left = lfFiles.get(i).hunks().get(0);
      Hunk right = crlfFiles.get(i).hunks().get(0);
      assertEquals(lfFiles.get(i).hunks().size(), crlfFiles.get(i).hunks().size(), "hunk 数不一致");
      assertEquals(left.oldCount(), right.oldCount());
      assertEquals(left.newCount(), right.newCount());
      assertEquals(left.lines().size(), right.lines().size(), "行数不一致：\\r 被当成了新行");
    }
  }

  @Test
  void carriageReturnIsContentNotSeparator() throws IOException {
    Hunk hunk = new UnifiedDiffParser().parse(read("pr1.diff")).get(0).hunks().get(0);

    long endingWithCr = hunk.lines().stream().filter(line -> line.content().endsWith("\r")).count();
    assertEquals(14, endingWithCr, "14 个 + 行的内容末尾各有一个 \\r，剥掉就是吃掉内容");
    assertEquals("  }\r", hunk.lines().get(20).content(), "第 21 行内容必须与源文件逐字相同");
    assertEquals("}", hunk.lines().get(21).content(), "末行在文件里没有换行符，内容不含 \\r");
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
  @ValueSource(strings = {
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
  @ValueSource(strings = {
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

  @Test
  void hunkIsSnapshotOfConstructorArgument() {
    List<DiffLine> mine = new ArrayList<>();
    mine.add(new DiffLine(LineType.CONTEXT, "kept"));
    Hunk hunk = new Hunk(1, 1, 1, 1, mine);

    mine.add(new DiffLine(LineType.ADDED, "injected"));

    assertEquals(1, hunk.lines().size(), "构造后改入参集合，不得影响 Hunk 内部");
  }

  @Test
  void hunkRejectsWriteThroughAccessor() {
    List<DiffLine> mine = new ArrayList<>();
    mine.add(new DiffLine(LineType.CONTEXT, "kept"));
    Hunk hunk = new Hunk(1, 1, 1, 1, mine);

    try {
      hunk.lines().add(new DiffLine(LineType.ADDED, "injected"));
    } catch (UnsupportedOperationException expected) {
      // 允许以抛异常的方式拒绝写入；也允许"写不进去且不抛"。判的是结果，不是手段。
    }

    assertEquals(1, hunk.lines().size(), "通过访问器写入不得改变 Hunk 的可观测状态");
  }

  @Test
  void diffFileIsSnapshotOfConstructorArgument() {
    List<Hunk> mine = new ArrayList<>();
    mine.add(new Hunk(1, 1, 1, 1, List.of(new DiffLine(LineType.CONTEXT, "kept"))));
    DiffFile file = new DiffFile("a.java", "a.java", FileKind.MODIFIED, mine);

    mine.add(new Hunk(0, 0, 1, 1, List.of()));

    assertEquals(1, file.hunks().size(), "构造后改入参集合，不得影响 DiffFile 内部");
  }

  @Test
  void diffFileRejectsWriteThroughAccessor() {
    List<Hunk> mine = new ArrayList<>();
    mine.add(new Hunk(1, 1, 1, 1, List.of(new DiffLine(LineType.CONTEXT, "kept"))));
    DiffFile file = new DiffFile("a.java", "a.java", FileKind.MODIFIED, mine);

    try {
      file.hunks().add(new Hunk(0, 0, 1, 1, List.of()));
    } catch (UnsupportedOperationException expected) {
      // 同上
    }

    assertEquals(1, file.hunks().size(), "通过访问器写入不得改变 DiffFile 的可观测状态");
  }

  @Test
  void pr1AnnotatedLinesCarryFileLineNumbers() throws IOException {
    Hunk hunk = new UnifiedDiffParser().parse(read("pr1.diff")).get(0).hunks().get(0);
    List<AnnotatedLine> rows = hunk.annotated();

    assertEquals(22, rows.size());
    assertEquals(1, rows.get(0).oldLine(), "首行是 REMOVED，旧号 1");
    assertEquals(0, rows.get(0).newLine(), "REMOVED 行在新侧不存在");
    assertEquals(7, rows.get(6).oldLine());
    assertEquals(1, rows.get(7).newLine(), "第 8 行是第一条 ADDED，新号 1");
    assertEquals(0, rows.get(7).oldLine(), "ADDED 行在旧侧不存在");
    assertEquals(14, rows.get(20).newLine());
    assertEquals(8, rows.get(21).oldLine(), "末行 CONTEXT，旧号 8 = 1+8-1");
    assertEquals(15, rows.get(21).newLine(), "末行 CONTEXT，新号 15 = 1+15-1");
    assertTrue(rows.get(21).line().noNewline(), "折叠标志要能在带号的行上读到");
  }

  @Test
  void multiHunkOneInterleavesAddsAndContexts() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));
    List<AnnotatedLine> rows = files.get(0).hunks().get(0).annotated();

    assertEquals(12, rows.size());
    assertEquals(1, rows.get(0).oldLine());
    assertEquals(1, rows.get(0).newLine());
    assertEquals(0, rows.get(2).oldLine(), "第 3 行是 ADDED，旧侧不占号");
    assertEquals(3, rows.get(2).newLine());
    assertEquals(3, rows.get(3).oldLine(), "第 4 行 old 只到 3：前面只有 1 条 ADDED");
    assertEquals(4, rows.get(3).newLine());
    assertEquals(9, rows.get(11).oldLine());
    assertEquals(12, rows.get(11).newLine());
  }

  @Test
  void degenerateHunkKeepsOldSideAtZero() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));

    List<AnnotatedLine> second = files.get(1).hunks().get(0).annotated();
    assertEquals(37, second.size());
    assertTrue(second.stream().allMatch(row -> row.oldLine() == 0), "oldCount=0 时旧号恒为 0");
    assertEquals(1, second.get(0).newLine());
    assertEquals(37, second.get(36).newLine());

    List<AnnotatedLine> third = files.get(2).hunks().get(0).annotated();
    assertEquals(28, third.size());
    assertTrue(third.stream().allMatch(row -> row.oldLine() == 0));
    assertEquals(28, third.get(27).newLine());
  }

  @Test
  void hunkLineCountsAreAnchored() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));

    assertEquals(12, files.get(0).hunks().get(0).lines().size(), "hunk1");
    assertEquals(37, files.get(1).hunks().get(0).lines().size(), "hunk2");
    assertEquals(28, files.get(2).hunks().get(0).lines().size(), "hunk3");
  }

  @Test
  void lastLineSitsOnTheRangeBoundary() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));
    for (DiffFile file : files) {
      for (Hunk hunk : file.hunks()) {
        List<AnnotatedLine> rows = hunk.annotated();
        AnnotatedLine last = rows.get(rows.size() - 1);
        if (hunk.oldCount() > 0) {
          assertEquals(hunk.oldStart() + hunk.oldCount() - 1, last.oldLine(), "旧侧末行守恒");
        } else {
          assertEquals(0, last.oldLine(), "oldCount=0 时末行旧号必须是 0，不是 -1");
        }
        if (hunk.newCount() > 0) {
          assertEquals(hunk.newStart() + hunk.newCount() - 1, last.newLine(), "新侧末行守恒");
        }
      }
    }
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
