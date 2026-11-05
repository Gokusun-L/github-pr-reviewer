package com.gokusun.prreviewer.review;

import java.util.List;

/**
 * 一次 prompt 构建的产物。
 *
 * <p>只有四个组件，全部是"事实"：两段文本 + 两个文件名清单。
 * <b>没有 truncated 字段</b>：它恒等于 !skippedFiles.isEmpty()，存下来就是第二份真值。
 */
public record BuiltPrompt(
    String systemPrompt, String userPrompt, List<String> renderedFiles, List<String> skippedFiles) {

  public BuiltPrompt {
    renderedFiles = List.copyOf(renderedFiles);
    skippedFiles = List.copyOf(skippedFiles);
  }

  /** 派生，不存字段。 */
  public boolean truncated() {
    return !skippedFiles.isEmpty();
  }

  /** 一个可审文件都没有：调用方据此跳过 LLM 调用，别空发一次请求。 */
  public boolean nothingToReview() {
    return renderedFiles.isEmpty();
  }
}
