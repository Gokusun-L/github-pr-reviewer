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

    long endingWithCr =
        hunk.lines().stream().filter(line -> line.content().endsWith("\r")).count();
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
