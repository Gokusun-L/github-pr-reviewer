package com.gokusun.prreviewer.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 解析 unified diff 文本，还原每条变更行在新文件中的真实行号。 */
@Component
public class UnifiedDiffParser {

  private static final String FILE_HEADER = "diff --git ";
  private static final String HUNK_HEADER = "@@ ";
  private static final String NO_NEWLINE_MARKER = "\\";
  private static final Pattern HUNK_HEADER_PATTERN =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

  /**
   * 解析 diff 文本。
   *
   * @param diffText unified diff 原文
   * @return 可审查的文件列表；二进制文件与被删除的文件不包含在内
   */
  public List<FileDiff> parse(String diffText) {
    if (diffText == null || diffText.isBlank()) {
      return List.of();
    }
    List<String> lines = splitLines(diffText);
    List<FileDiff> fileDiffs = new ArrayList<>();
    int index = 0;
    while (index < lines.size()) {
      if (!lines.get(index).startsWith(FILE_HEADER)) {
        index++;
        continue;
      }
      FileBlock block = new FileBlock();
      block.path = pathFromFileHeader(lines.get(index));
      index++;
      while (index < lines.size() && !lines.get(index).startsWith(FILE_HEADER)) {
        String line = lines.get(index);
        if (line.startsWith(HUNK_HEADER)) {
          index = parseHunk(lines, index, block);
          continue;
        }
        if (line.startsWith("deleted file mode")) {
          block.deleted = true;
        } else if (line.startsWith("Binary files") || line.startsWith("GIT binary patch")) {
          block.binary = true;
        } else if (line.startsWith("+++ ")) {
          String path = stripPathPrefix(line.substring(4));
          if ("/dev/null".equals(path)) {
            block.deleted = true;
          } else {
            block.path = path;
          }
        }
        index++;
      }
      if (!block.deleted && !block.binary && block.path != null) {
        fileDiffs.add(new FileDiff(block.path, block.hunks));
      }
    }
    return fileDiffs;
  }

  /** 解析一个 hunk 及其正文，返回下一个待处理行的下标。 */
  private int parseHunk(List<String> lines, int index, FileBlock block) {
    Matcher matcher = HUNK_HEADER_PATTERN.matcher(lines.get(index));
    if (!matcher.find()) {
      return index + 1;
    }
    int oldStart = Integer.parseInt(matcher.group(1));
    int oldCount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
    int newStart = Integer.parseInt(matcher.group(3));
    int newCount = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
    index++;

    int oldNo = oldStart;
    int newNo = newStart;
    int oldUsed = 0;
    int newUsed = 0;
    List<ChangedLine> hunkLines = new ArrayList<>();

    while (index < lines.size() && (oldUsed < oldCount || newUsed < newCount)) {
      String line = lines.get(index);
      if (line.startsWith(FILE_HEADER) || line.startsWith(HUNK_HEADER)) {
        break;
      }
      if (line.startsWith(NO_NEWLINE_MARKER)) {
        index++;
        continue;
      }
      char prefix = line.isEmpty() ? ' ' : line.charAt(0);
      if (prefix == '+') {
        hunkLines.add(new ChangedLine(block.path, ChangeType.ADDED, -1, newNo, line.substring(1)));
        newNo++;
        newUsed++;
      } else if (prefix == '-') {
        hunkLines.add(new ChangedLine(block.path, ChangeType.REMOVED, oldNo, -1, line.substring(1)));
        oldNo++;
        oldUsed++;
      } else {
        String content = line.isEmpty() ? "" : line.substring(1);
        hunkLines.add(new ChangedLine(block.path, ChangeType.CONTEXT, oldNo, newNo, content));
        oldNo++;
        newNo++;
        oldUsed++;
        newUsed++;
      }
      index++;
    }

    block.hunks.add(new Hunk(oldStart, oldCount, newStart, newCount, hunkLines));
    return index;
  }

  /** 按行切分并去掉行尾的 CR。 */
  private static List<String> splitLines(String diffText) {
    String[] raw = diffText.split("\n", -1);
    List<String> lines = new ArrayList<>(raw.length);
    for (String line : raw) {
      lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
    }
    if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    return lines;
  }

  /** 从 `diff --git a/... b/...` 取新文件路径。 */
  private static String pathFromFileHeader(String line) {
    int marker = line.lastIndexOf(" b/");
    if (marker < 0) {
      return null;
    }
    return line.substring(marker + 3);
  }

  /** 去掉 `a/` 或 `b/` 前缀。 */
  private static String stripPathPrefix(String rawPath) {
    String path = rawPath.trim();
    if (path.startsWith("b/") || path.startsWith("a/")) {
      return path.substring(2);
    }
    return path;
  }

  private static class FileBlock {
    private String path;
    private boolean binary;
    private boolean deleted;
    private final List<Hunk> hunks = new ArrayList<>();
  }
}