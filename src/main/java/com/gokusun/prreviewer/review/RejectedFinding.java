package com.gokusun.prreviewer.review;

import java.util.Objects;

/**
 * 被丢弃的一条 finding 加上丢弃原因。
 *
 * <p>{@code reason} 不许为 null —— 两个都 null 等于"丢了但不知道为什么"。
 */
public record RejectedFinding(Finding finding, FindingRejectReason reason) {

  public RejectedFinding {
    Objects.requireNonNull(reason, "reason");
  }
}
