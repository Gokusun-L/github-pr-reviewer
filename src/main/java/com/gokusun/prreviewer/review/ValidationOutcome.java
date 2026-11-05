package com.gokusun.prreviewer.review;

import java.util.List;

/** 一批候选 finding 的校验结果：留下的，和丢掉的（带原因）。 */
public record ValidationOutcome(List<Finding> kept, List<RejectedFinding> rejected) {

  public ValidationOutcome {
    kept = List.copyOf(kept);
    rejected = List.copyOf(rejected);
  }
}
