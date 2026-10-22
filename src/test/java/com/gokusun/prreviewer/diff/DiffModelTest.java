package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffModelTest {

  private static ChangedLine added(int newLineNo, String content) {
    return new ChangedLine(ChangeType.ADDED, null, newLineNo, content);
  }

  private static ChangedLine removed(int oldLineNo, String content) {
    return new ChangedLine(ChangeType.REMOVED, oldLineNo, null, content);
  }

  private static Hunk hunkOf(List<ChangedLine> lines) {
    return new Hunk(1, 2, 1, 3, null, lines);
  }

  @Test
  void hunkCopiesLinesDefensively() {
    List<ChangedLine> source = new ArrayList<>();
    source.add(added(1, "a"));
    Hunk hunk = hunkOf(source);

    source.add(added(2, "b"));

    assertEquals(1, hunk.lines().size());
  }

  @Test
  void fileDiffCopiesHunksDefensively() {
    List<Hunk> source = new ArrayList<>();
    source.add(hunkOf(List.of(removed(1, "x"))));
    FileDiff fileDiff =
        new FileDiff("src/Old.java", "src/Old.java", FileStatus.MODIFIED, false, source);

    source.clear();

    assertEquals(1, fileDiff.hunks().size());
  }

  @Test
  void hunkRejectsNullLine() {
    List<ChangedLine> withNull = new ArrayList<>();
    withNull.add(null);

    assertThrows(NullPointerException.class, () -> hunkOf(withNull));
  }

  @Test
  void lineNumbersAreNullOnTheSideThatDoesNotExist() {
    ChangedLine add = added(7, "+line");
    ChangedLine remove = removed(3, "-line");

    assertNull(add.oldLineNo());
    assertEquals(7, add.newLineNo());
    assertEquals(3, remove.oldLineNo());
    assertNull(remove.newLineNo());
  }

  @Test
  void binaryFileCarriesNoHunks() {
    FileDiff binary = new FileDiff(null, "assets/logo.png", FileStatus.ADDED, true, List.of());

    assertTrue(binary.binary());
    assertTrue(binary.hunks().isEmpty());
  }
}
