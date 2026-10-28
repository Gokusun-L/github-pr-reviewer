package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void crlfSampleAndLfSampleBothResolvePaths() throws IOException {
    String crlfText = read("pr1.diff");
    String lfText = read("multi-file.diff");

    assertTrue(crlfText.contains("\r"), "pr1.diff 必须带 CR，否则 CRLF 分支等于没覆盖");
    assertTrue(!lfText.contains("\r"), "multi-file.diff 必须是不带 CR 的 LF 样本");

    List<DiffFile> crlfFiles = new UnifiedDiffParser().parse(crlfText);
    List<DiffFile> lfFiles = new UnifiedDiffParser().parse(lfText);

    assertEquals("src/main/java/demo/Calculator.java", crlfFiles.get(0).newPath());
    assertEquals(
        "src/main/java/com/gokusun/prreviewer/PrReviewerApplication.java",
        lfFiles.get(0).newPath());
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
  void dirtySamplesDoNotThrowOnDisplayName(String sample) throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read(sample));

    for (DiffFile file : files) {
      assertDoesNotThrow(file::displayName);
    }
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
