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

  /**
   * 严格解析：任何一个文件坏掉都整体失败。
   *
   * @throws IllegalStateException 有文件解析失败时，消息里带每个失败文件的路径与原因
   */
  public List<DiffFile> parse(String diffText) {
    ParseReport report = parseReport(diffText);
    if (!report.failures().isEmpty()) {
      StringBuilder text = new StringBuilder("diff 解析失败：");
      for (ParseFailure failure : report.failures()) {
        text.append("\n  ").append(failure.path()).append(": ").append(failure.reason());
      }
      throw new IllegalStateException(text.toString());
    }
    return report.files();
  }

  /**
   * 宽松解析：坏文件单独记录，不影响其他文件。
   *
   * @return 成功文件与失败文件的合集；输入为空白时两者皆空
   */
  public ParseReport parseReport(String diffText) {
    if (diffText == null || diffText.isBlank()) {
      return new ParseReport(List.of(), List.of());
    }
    Pass pass = new Pass();
    for (String line : diffText.split("\n")) {
      pass.accept(line);
    }
    pass.finishFile();
    return pass.toReport();
  }

  /** 一次解析调用的可变状态容器；生命周期与 parseReport 的一次调用相同。 */
  private static final class Pass {

    private final List<DiffFile> files = new ArrayList<>();
    private final List<ParseFailure> failures = new ArrayList<>();
    private final List<Hunk> hunks = new ArrayList<>();

    private State state = State.OUTSIDE;
    private String oldPath;
    private String newPath;
    private boolean added;
    private boolean deleted;
    private boolean renamed;
    private HunkDraft draft;
    private boolean broken;
    private String brokenReason;

    private void accept(String line) {
      if (line.isEmpty()) {
        return;
      }

      if (line.startsWith(DIFF_GIT)) {
        flushHunk();
        finishFile();
        String[] defaultPaths = pathsFromDiffGit(line.substring(DIFF_GIT.length()));
        oldPath = defaultPaths[0];
        newPath = defaultPaths[1];
        added = false;
        deleted = false;
        renamed = false;
        state = State.HEADER;
        return;
      }

      if (state == State.OUTSIDE) {
        return;
      }

      if (state == State.HEADER) {
        acceptHeader(line);
        return;
      }

      if (line.startsWith("@@")) {
        flushHunk();
        Matcher matcher = HUNK_HEADER.matcher(line);
        if (matcher.find()) {
          draft = HunkDraft.of(matcher);
        } else {
          draft = null;
          state = State.HEADER;
        }
        return;
      }

      if (line.charAt(0) == '\\') {
        if (draft != null && !draft.lines.isEmpty()) {
          int last = draft.lines.size() - 1;
          draft.lines.set(last, draft.lines.get(last).withNoNewline());
        }
        return;
      }

      LineType type = classify(line.charAt(0));
      if (type != null && draft != null) {
        draft.lines.add(new DiffLine(type, line.substring(1)));
      }
    }

    private void acceptHeader(String line) {
      if (line.startsWith("--- ")) {
        oldPath = decodeGitPath(line.substring(4));
        added = oldPath == null;
        return;
      }
      if (line.startsWith("+++ ")) {
        newPath = decodeGitPath(line.substring(4));
        deleted = newPath == null;
        return;
      }
      if (line.startsWith("new file mode ")) {
        added = true;
        return;
      }
      if (line.startsWith("deleted file mode ")) {
        deleted = true;
        return;
      }
      if (line.startsWith("rename from ") || line.startsWith("rename to ")) {
        renamed = true;
        return;
      }
      Matcher matcher = HUNK_HEADER.matcher(line);
      if (matcher.find()) {
        draft = HunkDraft.of(matcher);
        state = State.IN_HUNK;
      }
    }

    private void flushHunk() {
      if (draft == null) {
        return;
      }
      try {
        hunks.add(draft.toHunk());
      } catch (RuntimeException e) {
        broken = true;
        brokenReason = e.getMessage();
      } finally {
        draft = null;
      }
    }

    private void finishFile() {
      if (state == State.OUTSIDE) {
        return;
      }
      flushHunk();
      if (broken) {
        failures.add(new ParseFailure(displayPath(), brokenReason));
      } else {
        files.add(new DiffFile(oldPath, newPath, kindOf(added, deleted, renamed), hunks));
      }
      hunks.clear();
      broken = false;
      brokenReason = null;
      state = State.OUTSIDE;
    }

    private String displayPath() {
      if (newPath != null) {
        return newPath;
      }
      if (oldPath != null) {
        return oldPath;
      }
      return "(未知路径)";
    }

    private ParseReport toReport() {
      return new ParseReport(files, failures);
    }
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
