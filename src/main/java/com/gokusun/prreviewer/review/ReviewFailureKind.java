package com.gokusun.prreviewer.review;

/** 整体失败的分类。W2 的 L2 降级要按它决定是否值得重试。 */
public enum ReviewFailureKind {
  /** 调用本身失败（超时 / 401 / 5xx）。 */
  CALL_FAILED,
  /** 拿到了文本，但 3 次都解析不出合法 JSON。 */
  PARSE_FAILED,
  /** 有可审文件，但一个都放不进字符预算。 */
  BUDGET_EXHAUSTED
}
