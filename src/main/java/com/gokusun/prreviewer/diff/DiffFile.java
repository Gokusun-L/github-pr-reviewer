package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 单个文件的 diff。
 */
public record DiffFile(String oldPath, String newPath, List<Hunk> hunks) {

  public String displayName() {
    return newPath.equals("/dev/null") ? oldPath : newPath;
  }
}
