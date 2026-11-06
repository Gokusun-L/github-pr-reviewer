package com.gokusun.prreviewer.review;

/** 单条 finding 被丢弃的原因。做成枚举而不是异常文案：要能统计误报率（W3）。 */
public enum FindingRejectReason {
  /** filePath 不等于任何 DiffFile.newPath()。删除文件（newPath == null）必然落到这里。 */
  FILE_NOT_IN_DIFF,

  /**
   * line == 0：AnnotatedLine 里"该侧不存在"的哨兵，说明模型指的是旧侧或写了 0。不纠正。
   *
   * <p>模型没给 line 时是 null，落 {@link #MISSING_FIELD}，不进这里。
   */
  ZERO_LINE,

  /** 该文件新侧没有这个行号（越界 / 负数 / 那是旧侧的行号）。 */
  NO_SUCH_NEW_LINE,

  /** 号存在，但那一行是 CONTEXT（未变更），违反"只审变更行"。 */
  NOT_CHANGED_LINE,

  /** severity 缺失 / message 缺失或空白。 */
  MISSING_FIELD
}
