package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 单个文件的 diff。路径已去掉 a/ b/ 前缀；/dev/null 一侧以 null 表示。
 */
public record DiffFile(String oldPath, String newPath, FileKind kind, List<Hunk> hunks) {

  public String displayName() {
    return newPath == null ? oldPath : newPath;
  }
}
