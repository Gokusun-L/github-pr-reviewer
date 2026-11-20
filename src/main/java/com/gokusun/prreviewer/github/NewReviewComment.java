package com.gokusun.prreviewer.github;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 行内评论请求体。
 *
 * @param body 评论正文
 * @param commitId 评论挂载的 commit SHA，序列化为 {@code commit_id}
 * @param path 新版本文件路径
 * @param line 新版本文件中的行号
 * @param side 固定 RIGHT，表示新版本一侧
 */
public record NewReviewComment(
    String body,
    @JsonProperty("commit_id") String commitId,
    String path,
    int line,
    String side) {}
