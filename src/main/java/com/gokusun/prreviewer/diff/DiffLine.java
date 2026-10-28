package com.gokusun.prreviewer.diff;

/**
 * diff 中的一行。
 *
 * <p>content 不含行首的分类字符，且<b>不做任何行尾处理</b>：{@code \r} 属于内容。
 * 依据是 GitHub API 用 {@code \n} 分行（见 S6 须知 §二 实测：628 B / LF=28 / CR=14，
 * 若 CRLF 分隔则 CR 应为 28）。消费者不得再对 content 做 strip。
 *
 * <p>{@code noNewline} 表示这一行在文件里没有行尾换行符，对应 diff 里的
 * {@code \ No newline at end of file}。它不是一条独立的行，不占行号。
 */
public record DiffLine(LineType type, String content, boolean noNewline) {

  public DiffLine(LineType type, String content) {
    this(type, content, false);
  }

  /** 同一行的"文件末尾无换行符"变体。 */
  public DiffLine withNoNewline() {
    return new DiffLine(type, content, true);
  }
}
