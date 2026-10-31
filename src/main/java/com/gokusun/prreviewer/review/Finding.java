package com.gokusun.prreviewer.review;

/**
 * 一条审查发现。字段是扁平的：BeanOutputConverter 会由它生成 JSON schema，
 * 嵌套对象会拉高模型输出非法 JSON 的概率，也对不上 W3 的 review_finding 表。
 *
 * <p>{@code line} 的语义被契约钉死：<b>新文件真实行号</b>（GitHub 行内评论的 side=RIGHT）。
 * 这里没有 side 字段 —— 见设计要点 D2，坐标系统一为"新侧"，指错侧靠数值校验发现。
 *
 * <p>本 record 不做任何校验：合法性需要 diff 知识，归 FindingValidator。
 * 这里抛异常会把拒绝理由逼成散落的异常文案，破坏"拒绝理由只有一个出口"。
 */
public record Finding(
    String filePath,
    int line,
    Severity severity,
    String message,
    String suggestion) {

  public Finding {
    // 空实现是**有意的**：校验需要 diff 知识，见类注释与 D2 / D6。
    // 唯一允许注意的点：suggestion 允许为 null（模型常不给），message 不允许 ——
    // 但"不允许"由 FindingValidator 判 MISSING_FIELD，不在这里抛。
  }
}
