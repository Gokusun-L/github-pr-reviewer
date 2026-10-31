package com.gokusun.prreviewer.review;

import java.util.List;

/**
 * 模型输出的顶层结构，也就是 BeanOutputConverter 的目标类型。
 *
 * <p>存在的意义：给 converter 一个可生成 schema 的"壳"，让模型的 JSON 有一层包装
 * （顶层直接是数组时，模型更容易把 JSON 裹进 ```json 围栏或加解释性前言）。
 */
public record LlmReview(List<Finding> findings) {

  public LlmReview {
    // findings 可能是 null —— Jackson 对缺失的 List 组件给 null，模型回 {} 就是这种情况。
    // null 一律归一成空列表，**不要抛**：模型说"没问题"是合法回答。
    // 真正的失败（JSON 非法）由 converter 抛异常，两条通道不许混用（D6）。
    findings = findings == null ? List.of() : List.copyOf(findings);
  }
}
