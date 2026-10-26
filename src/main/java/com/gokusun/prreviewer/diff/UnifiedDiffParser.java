package com.gokusun.prreviewer.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnifiedDiffParser {

  private static final Pattern HUNK_HEADER =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

  private enum State {
    OUTSIDE,
    HEADER,
    IN_HUNK
  }

  public List<DiffFile> parse(String diffText) {
    List<DiffFile> files = new ArrayList<>();
    if (diffText == null || diffText.isBlank()) {
      return files;
    }

    State state = State.OUTSIDE;
    String oldPath = null;
    String newPath = null;
    List<Hunk> hunks = new ArrayList<>();
    HunkDraft draft = null;

    for (String raw : diffText.split("\n")) {
      String line = stripTrailingCr(raw);
      if (line.isEmpty()) {
        continue;
      }

      if (line.startsWith("diff --git ")) {
        if (draft != null) {
          hunks.add(draft.toHunk());
          draft = null;
        }
        if (state != State.OUTSIDE) {
          files.add(new DiffFile(oldPath, newPath, hunks));
          hunks = new ArrayList<>();
        }
        oldPath = null;
        newPath = null;
        state = State.HEADER;
        continue;
      }

      if (state == State.OUTSIDE) {
        continue;
      }

      if (state == State.HEADER) {
        if (line.startsWith("--- ")) {
          oldPath = toPath(line.substring(4));
          continue;
        }
        if (line.startsWith("+++ ")) {
          newPath = toPath(line.substring(4));
          continue;
        }
        Matcher matcher = HUNK_HEADER.matcher(line);
        if (matcher.find()) {
          draft = HunkDraft.of(matcher);
          state = State.IN_HUNK;
        }
        continue;
      }

      if (line.startsWith("@@")) {
        hunks.add(draft.toHunk());
        Matcher matcher = HUNK_HEADER.matcher(line);
        if (matcher.find()) {
          draft = HunkDraft.of(matcher);
        } else {
          draft = null;
          state = State.HEADER;
        }
        continue;
      }

      LineType type = classify(line.charAt(0));
      if (type != null) {
        draft.lines.add(new DiffLine(type, line.substring(1)));
      }
    }

    if (draft != null) {
      hunks.add(draft.toHunk());
    }
    if (state != State.OUTSIDE) {
      files.add(new DiffFile(oldPath, newPath, hunks));
    }
    return files;
  }

  private static String stripTrailingCr(String line) {
    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
  }

  private static String toPath(String raw) {
    if (raw.equals("/dev/null")) {
      return raw;
    }
    if (raw.startsWith("a/") || raw.startsWith("b/")) {
      return raw.substring(2);
    }
    return raw;
  }

  private static LineType classify(char prefix) {
    return switch (prefix) {
      case ' ' -> LineType.CONTEXT;
      case '+' -> LineType.ADDED;
      case '-' -> LineType.REMOVED;
      case '\\' -> LineType.NO_NEWLINE;
      default -> null;
    };
  }

  private static final class HunkDraft {

    private final int oldStart;
    private final int oldCount;
    private final int newStart;
    private final int newCount;
    private final List<DiffLine> lines = new ArrayList<>();

    private HunkDraft(int oldStart, int oldCount, int newStart, int newCount) {
      this.oldStart = oldStart;
      this.oldCount = oldCount;
      this.newStart = newStart;
      this.newCount = newCount;
    }

    private static HunkDraft of(Matcher matcher) {
      int oldStart = Integer.parseInt(matcher.group(1));
      int oldCount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
      int newStart = Integer.parseInt(matcher.group(3));
      int newCount = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
      return new HunkDraft(oldStart, oldCount, newStart, newCount);
    }

    private Hunk toHunk() {
      long context = count(LineType.CONTEXT);
      long added = count(LineType.ADDED);
      long removed = count(LineType.REMOVED);
      if (context + removed != oldCount || context + added != newCount) {
        throw new IllegalStateException(
            "hunk 计数不守恒: @@ -"
                + oldStart
                + ","
                + oldCount
                + " +"
                + newStart
                + ","
                + newCount
                + " @@ 实际 context="
                + context
                + " added="
                + added
                + " removed="
                + removed);
      }
      return new Hunk(oldStart, oldCount, newStart, newCount, lines);
    }

    private long count(LineType type) {
      return lines.stream().filter(line -> line.type() == type).count();
    }
  }
}
