package com.gokusun.prreviewer.diff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnifiedDiffParser {

  private static final String DIFF_GIT = "diff --git ";
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
    boolean added = false;
    boolean deleted = false;
    boolean renamed = false;
    List<Hunk> hunks = new ArrayList<>();
    HunkDraft draft = null;

    for (String raw : diffText.split("\n")) {
      String line = stripTrailingCr(raw);
      if (line.isEmpty()) {
        continue;
      }

      if (line.startsWith(DIFF_GIT)) {
        if (draft != null) {
          hunks.add(draft.toHunk());
          draft = null;
        }
        if (state != State.OUTSIDE) {
          files.add(new DiffFile(oldPath, newPath, kindOf(added, deleted, renamed), hunks));
          hunks = new ArrayList<>();
        }
        String[] defaultPaths = pathsFromDiffGit(line.substring(DIFF_GIT.length()));
        oldPath = defaultPaths[0];
        newPath = defaultPaths[1];
        added = false;
        deleted = false;
        renamed = false;
        state = State.HEADER;
        continue;
      }

      if (state == State.OUTSIDE) {
        continue;
      }

      if (state == State.HEADER) {
        if (line.startsWith("--- ")) {
          oldPath = decodeGitPath(line.substring(4));
          added = oldPath == null;
          continue;
        }
        if (line.startsWith("+++ ")) {
          newPath = decodeGitPath(line.substring(4));
          deleted = newPath == null;
          continue;
        }
        if (line.startsWith("new file mode ")) {
          added = true;
          continue;
        }
        if (line.startsWith("deleted file mode ")) {
          deleted = true;
          continue;
        }
        if (line.startsWith("rename from ") || line.startsWith("rename to ")) {
          renamed = true;
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

      if (line.charAt(0) == '\\') {
        if (!draft.lines.isEmpty()) {
          int last = draft.lines.size() - 1;
          draft.lines.set(last, draft.lines.get(last).withNoNewline());
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
      files.add(new DiffFile(oldPath, newPath, kindOf(added, deleted, renamed), hunks));
    }
    return files;
  }

  private static FileKind kindOf(boolean added, boolean deleted, boolean renamed) {
    if (renamed) {
      return FileKind.RENAMED;
    }
    if (added) {
      return FileKind.ADDED;
    }
    if (deleted) {
      return FileKind.DELETED;
    }
    return FileKind.MODIFIED;
  }

  private static String stripTrailingCr(String line) {
    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
  }

  private static String[] pathsFromDiffGit(String rest) {
    String trimmed = rest.trim();
    if (trimmed.isEmpty()) {
      return new String[] {null, null};
    }
    if (trimmed.startsWith("\"")) {
      int closing = findClosingQuote(trimmed, 0);
      if (closing < 0) {
        return new String[] {null, decodeGitPath(trimmed)};
      }
      String first = trimmed.substring(0, closing + 1);
      String remainder = trimmed.substring(closing + 1).trim();
      return new String[] {
        decodeGitPath(first), remainder.isEmpty() ? null : decodeGitPath(remainder)
      };
    }
    int middle = trimmed.lastIndexOf(" b/");
    if (middle < 0) {
      return new String[] {null, decodeGitPath(trimmed)};
    }
    return new String[] {
      decodeGitPath(trimmed.substring(0, middle)), decodeGitPath(trimmed.substring(middle + 1))
    };
  }

  private static int findClosingQuote(String text, int openIndex) {
    for (int i = openIndex + 1; i < text.length(); i++) {
      if (text.charAt(i) == '\\') {
        i++;
        continue;
      }
      if (text.charAt(i) == '"') {
        return i;
      }
    }
    return -1;
  }

  private static String decodeGitPath(String token) {
    if (token == null || token.isEmpty()) {
      return token;
    }
    String raw = token;
    if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
      raw = unescape(raw.substring(1, raw.length() - 1));
    }
    if (raw.equals("/dev/null")) {
      return null;
    }
    if (raw.startsWith("a/") || raw.startsWith("b/")) {
      return raw.substring(2);
    }
    return raw;
  }

  private static String unescape(String text) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (current != '\\' || i + 1 >= text.length()) {
        out.write((byte) current);
        continue;
      }
      char next = text.charAt(++i);
      if (next >= '0' && next <= '7') {
        int value = next - '0';
        int digits = 1;
        while (digits < 3
            && i + 1 < text.length()
            && text.charAt(i + 1) >= '0'
            && text.charAt(i + 1) <= '7') {
          value = value * 8 + (text.charAt(++i) - '0');
          digits++;
        }
        out.write(value);
      } else {
        out.write(
            switch (next) {
              case 'n' -> '\n';
              case 't' -> '\t';
              case 'r' -> '\r';
              case '"' -> '"';
              case '\\' -> '\\';
              default -> (byte) next;
            });
      }
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  private static LineType classify(char prefix) {
    return switch (prefix) {
      case ' ' -> LineType.CONTEXT;
      case '+' -> LineType.ADDED;
      case '-' -> LineType.REMOVED;
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
      long addedLines = count(LineType.ADDED);
      long removed = count(LineType.REMOVED);
      if (context + removed != oldCount || context + addedLines != newCount) {
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
                + addedLines
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
