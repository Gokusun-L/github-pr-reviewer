package com.gokusun.prreviewer.review;

/**
 * LLM 调用的唯一出口。整个 S7 只有它的实现允许碰网络。
 *
 * <p>入参与出参都是 String：入参是已经拼好的 prompt，出参是模型原话（不保证是 JSON）。
 * 这样"拼 prompt / 解析 / 校验 / 重试"全部与 Spring AI 无关，单测直接 new 一个假实现。
 *
 * <p>为什么不用 Spring AI 的 ChatModel 做接口：那会让每个测试都拖上 Spring AI 的类型，
 * 而本项目的红线是"单测不真调 LLM、不加载上下文"。
 */
@FunctionalInterface
public interface ReviewModel {

  /**
   * @return 模型返回的原始文本，可能不是合法 JSON，可能为空。解析是调用方的事。
   * @throws ReviewFailedException 传输层失败（超时 / 401 / 5xx）
   */
  String invoke(String systemPrompt, String userPrompt);
}
