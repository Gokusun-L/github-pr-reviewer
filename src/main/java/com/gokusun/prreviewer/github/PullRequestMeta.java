package com.gokusun.prreviewer.github;

/**
 * PR 元数据中行内评论需要的坐标。
 *
 * @param headSha PR 当前 head commit 的 SHA
 */
public record PullRequestMeta(String headSha) {}
