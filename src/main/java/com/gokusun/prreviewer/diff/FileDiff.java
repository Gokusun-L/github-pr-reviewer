package com.gokusun.prreviewer.diff;

import java.util.List;

/**
 * 单个文件的 diff。
 *
 * @param oldPath 旧文件路径；新增文件时为 null
 * @param newPath 新文件路径；删除文件时为 null
 * @param binary  是否是二进制文件；二进制文件不携带 hunk
 */
public record FileDiff(
    String oldPath,
    String newPath,
    FileStatus status,
    boolean binary,
    List<Hunk> hunks) {

  public FileDiff {
    hunks = List.copyOf(hunks);
  }
}
