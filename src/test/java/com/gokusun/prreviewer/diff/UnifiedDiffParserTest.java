package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedDiffParserTest {

  private final UnifiedDiffParser parser = new UnifiedDiffParser();

  @Test
  void newFile_allLinesAreAdded() {
    String diff =
        String.join(
            "\n",
            "diff --git a/New.java b/New.java",
            "new file mode 100644",
            "index 0000000..1111111",
            "--- /dev/null",
            "+++ b/New.java",
            "@@ -0,0 +1,3 @@",
            "+line1",
            "+line2",
            "+line3");

    List<FileDiff> files = parser.parse(diff);

    assertEquals(1, files.size());
    assertEquals("New.java", files.get(0).path());
    List<ChangedLine> lines = files.get(0).hunks().get(0).lines();
    assertEquals(3, lines.size());
    for (int i = 0; i < 3; i++) {
      assertEquals(ChangeType.ADDED, lines.get(i).type());
      assertEquals(-1, lines.get(i).oldLineNo());
      assertEquals(i + 1, lines.get(i).newLineNo());
    }
    assertEquals("line1", lines.get(0).content());
  }

  @Test
  void modifiedFile_mapsAddedLinesToNewFileLineNumbers() {
    String diff =
        String.join(
            "\n",
            "diff --git a/A.java b/A.java",
            "index 1111111..2222222 100644",
            "--- a/A.java",
            "+++ b/A.java",
            "@@ -1,4 +1,5 @@",
            " package a;",
            "",
            "-old",
            "+new",
            "+extra",
            " keep");

    List<FileDiff> files = parser.parse(diff);

    assertEquals(1, files.size());
    List<ChangedLine> lines = files.get(0).hunks().get(0).lines();
    assertEquals(6, lines.size());
    assertEquals(new ChangedLine("A.java", ChangeType.CONTEXT, 1, 1, "package a;"), lines.get(0));
    assertEquals(new ChangedLine("A.java", ChangeType.CONTEXT, 2, 2, ""), lines.get(1));
    assertEquals(new ChangedLine("A.java", ChangeType.REMOVED, 3, -1, "old"), lines.get(2));
    assertEquals(new ChangedLine("A.java", ChangeType.ADDED, -1, 3, "new"), lines.get(3));
    assertEquals(new ChangedLine("A.java", ChangeType.ADDED, -1, 4, "extra"), lines.get(4));
    assertEquals(new ChangedLine("A.java", ChangeType.CONTEXT, 4, 5, "keep"), lines.get(5));
  }

  @Test
  void binaryFile_isSkipped() {
    String diff =
        String.join(
            "\n",
            "diff --git a/logo.png b/logo.png",
            "new file mode 100644",
            "index 0000000..3333333",
            "Binary files /dev/null and b/logo.png differ");

    assertTrue(parser.parse(diff).isEmpty());
  }

  @Test
  void deletedFile_isSkipped() {
    String diff =
        String.join(
            "\n",
            "diff --git a/Old.java b/Old.java",
            "deleted file mode 100644",
            "index 4444444..0000000",
            "--- a/Old.java",
            "+++ /dev/null",
            "@@ -1,2 +0,0 @@",
            "-line1",
            "-line2");

    assertTrue(parser.parse(diff).isEmpty());
  }

  @Test
  void multipleFiles_areParsedSeparately() {
    String diff =
        String.join(
            "\n",
            "diff --git a/A.txt b/A.txt",
            "index 1111111..2222222 100644",
            "--- a/A.txt",
            "+++ b/A.txt",
            "@@ -1 +1 @@",
            "-a",
            "+b",
            "diff --git a/B.txt b/B.txt",
            "index 3333333..4444444 100644",
            "--- a/B.txt",
            "+++ b/B.txt",
            "@@ -1 +1 @@",
            "-c",
            "+d");

    List<FileDiff> files = parser.parse(diff);

    assertEquals(2, files.size());
    assertEquals("A.txt", files.get(0).path());
    assertEquals("B.txt", files.get(1).path());
    assertEquals(
        new ChangedLine("A.txt", ChangeType.ADDED, -1, 1, "b"),
        files.get(0).hunks().get(0).lines().get(1));
    assertEquals(
        new ChangedLine("B.txt", ChangeType.ADDED, -1, 1, "d"),
        files.get(1).hunks().get(0).lines().get(1));
  }

  @Test
  void noNewlineMarker_doesNotConsumeLineNumber() {
    String diff =
        String.join(
            "\n",
            "diff --git a/N.java b/N.java",
            "index 1111111..2222222 100644",
            "--- a/N.java",
            "+++ b/N.java",
            "@@ -1,3 +1,3 @@",
            "-a",
            "+b",
            " c",
            "\\ No newline at end of file",
            " d");

    List<ChangedLine> lines = parser.parse(diff).get(0).hunks().get(0).lines();

    assertEquals(4, lines.size());
    assertEquals(new ChangedLine("N.java", ChangeType.CONTEXT, 2, 2, "c"), lines.get(2));
    assertEquals(new ChangedLine("N.java", ChangeType.CONTEXT, 3, 3, "d"), lines.get(3));
  }
}