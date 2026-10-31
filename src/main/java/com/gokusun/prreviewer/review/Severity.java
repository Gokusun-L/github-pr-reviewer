package com.gokusun.prreviewer.review;

/** 严重级。取值就是 prompt 里允许模型输出的全部合法值。 */
public enum Severity {
  BLOCKER,
  MAJOR,
  MINOR
}
