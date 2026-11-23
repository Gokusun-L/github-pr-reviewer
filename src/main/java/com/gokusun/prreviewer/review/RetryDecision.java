package com.gokusun.prreviewer.review;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 一次外呼失败的可重试判定。
 *
 * <p>全项目唯一实现：模型侧重试循环与发布侧失败标记都从这里取结论。
 */
public final class RetryDecision {

  private RetryDecision() {}

  /**
   * 判断该异常再试一次是否有意义。
   *
   * @param e 一次外呼抛出的异常；为 {@code null} 时按不可重试处理，不抛异常
   * @return 瞬时失败（模型 5xx、网络中断、5xx 状态码）为 {@code true}；确定性失败（模型 4xx、
   *     4xx 状态码、限流）与未知异常为 {@code false}
   */
  public static boolean retryable(Throwable e) {
    if (e instanceof TransientAiException) {
      return true;
    }
    if (e instanceof NonTransientAiException) {
      return false;
    }
    if (e instanceof ResourceAccessException) {
      return true;
    }
    if (e instanceof RestClientResponseException response) {
      int status = response.getStatusCode().value();
      if (status == 429 || status == 403) {
        return false;
      }
      return response.getStatusCode().is5xxServerError();
    }
    return false;
  }
}
