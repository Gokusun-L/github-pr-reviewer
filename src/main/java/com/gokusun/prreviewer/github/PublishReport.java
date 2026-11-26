package com.gokusun.prreviewer.github;

import java.util.List;

/**
 * 一次发布的结果快照。
 *
 * @param posted   成功创建的评论条数
 * @param failures 创建失败的评论及其坐标与状态码
 */
public record PublishReport(int posted, List<Failure> failures) {

  public PublishReport {
    if (posted < 0) {
      throw new IllegalArgumentException("posted 不许为负: " + posted);
    }
    failures = List.copyOf(failures);
  }

  /** 失败条数。 */
  public int failed() {
    return failures.size();
  }

  /**
   * 一条发布失败的坐标。
   *
   * @param path      文件路径
   * @param line      文件行号，可为 null
   * @param status    HTTP 状态码；网络失败拿不到响应，为 null
   * @param retryable 这次失败是否值得重试
   */
  public record Failure(String path, Integer line, Integer status, boolean retryable) {}
}
