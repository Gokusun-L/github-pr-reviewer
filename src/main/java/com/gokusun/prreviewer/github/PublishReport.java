package com.gokusun.prreviewer.github;

/**
 * 一次发布的结果快照。
 *
 * @param posted 成功创建的评论条数
 * @param failed 创建失败、被跳过继续的评论条数
 */
public record PublishReport(int posted, int failed) {

  public PublishReport {
    if (posted < 0 || failed < 0) {
      throw new IllegalArgumentException("计数不许为负: posted=" + posted + " failed=" + failed);
    }
  }
}
