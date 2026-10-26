package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {

  @Test
  void pr1_parsesSingleFileSingleHunk() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("pr1.diff"));

    assertEquals(1, files.size());
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
    assertTrue(files.get(1).oldPath().contains("/dev/null"));
    assertEquals(0, files.get(1).hunks().get(0).oldCount());
    assertEquals(0, files.get(2).hunks().get(0).oldCount());
  }

  @Test
  void crlfAndLfProduceSamePaths() throws IOException {
    List<DiffFile> files = new UnifiedDiffParser().parse(read("multi-file.diff"));

    assertEquals(
        "src/main/java/com/gokusun/prreviewer/PrReviewerApplication.java",
        files.get(0).newPath());
    assertEquals(
        "src/main/java/com/gokusun/prreviewer/github/GitHubClient.java", files.get(1).newPath());
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
