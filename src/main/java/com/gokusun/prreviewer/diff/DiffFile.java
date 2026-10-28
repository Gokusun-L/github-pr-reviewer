package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 单个文件的 diff。路径已去掉 a/ b/ 前缀；/dev/null 一侧以 null 表示。
 */
public record DiffFile(String oldPath, String newPath, FileKind kind, List<Hunk> hunks) {

  /**
   * 用于展示的路径，永不返回 null。
   *
   * <p>只用于展示。回写 PR 评论的 path 必须取 API 的 {@code files[].filename}。
   */
  public String displayName() {
    if (newPath != null) {
      return newPath;
    }
    if (oldPath != null) {
      return oldPath;
    }
    return "(unknown)";
  }
}
