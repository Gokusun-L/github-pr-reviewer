package com.gokusun.prreviewer.review;

/**
 * S7 的确定性失败出口。
 *
 * <p>为什么抛而不是返回空列表：空列表在下游与"审查通过、没发现问题"完全不可区分，
 * 就是"返回空结果但假装成功"。
 */
public class ReviewFailedException extends RuntimeException {

  private final ReviewFailureKind kind;
  private final int attempts;
  private final String rawSnippet;

  public ReviewFailedException(
      ReviewFailureKind kind, int attempts, String rawSnippet, String message, Throwable cause) {
    super(message, cause);
    if (kind == null) {
      throw new IllegalArgumentException("kind 不许为 null");
    }
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts 不许为负: " + attempts);
    }
    this.kind = kind;
    this.attempts = attempts;
    this.rawSnippet = rawSnippet;
  }

  public ReviewFailureKind kind() {
    return kind;
  }

  public int attempts() {
    return attempts;
  }

  /** 最后一次模型原话的前 200 字符，只用于排障。可能为 null（如 BUDGET_EXHAUSTED）。 */
  public String rawSnippet() {
    return rawSnippet;
  }
}
