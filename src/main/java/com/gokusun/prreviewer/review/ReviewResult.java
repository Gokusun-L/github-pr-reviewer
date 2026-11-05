package com.gokusun.prreviewer.review;

import java.util.List;

/** 一次成功审查的产物：留下的、丢掉的、没放进 prompt 的，外加真实请求次数。 */
public record ReviewResult(
    List<Finding> findings, List<RejectedFinding> rejected, List<String> skippedFiles, int attempts) {

  public ReviewResult {
    findings = List.copyOf(findings);
    rejected = List.copyOf(rejected);
    skippedFiles = List.copyOf(skippedFiles);
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts 不许为负: " + attempts);
    }
  }
}
