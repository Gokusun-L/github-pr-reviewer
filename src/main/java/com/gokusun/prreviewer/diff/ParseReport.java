package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 一次解析的完整产出：解析成功的文件 + 被丢弃的文件。
 *
 * <p>两边同时返回：全坏时 {@code files} 为空，全好时 {@code failures} 为空。
 */
public record ParseReport(List<DiffFile> files, List<ParseFailure> failures) {

  public ParseReport {
    files = List.copyOf(files);
    failures = List.copyOf(failures);
  }
}
