package com.gokusun.prreviewer.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 封装对 GitHub REST API 的调用。当前仅实现拉取 PR diff。 */
@Component
public class GitHubClient {

  /** 请求 diff 时 GitHub 要求的 Accept 媒体类型。 */
  private static final MediaType DIFF_ACCEPT = MediaType.parseMediaType("application/vnd.github.v3.diff");

  private final RestClient restClient;

  public GitHubClient(
      @Value("${github.api-base-url}") String apiBaseUrl,
      @Value("${github.token}") String token) {
    this.restClient =
        RestClient.builder()
            .baseUrl(apiBaseUrl)
            .defaultHeader("Authorization", "Bearer " + token)
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
  }

  /**
   * 拉取指定 PR 的 unified diff 文本。
   *
   * @return 原始 diff 文本（供 D3-D4 的 DiffParser 解析）
   */
  public String pullDiff(String owner, String repo, int number) {
    return restClient
        .get()
        .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
        .accept(DIFF_ACCEPT)
        .retrieve()
        .body(String.class);
  }
}