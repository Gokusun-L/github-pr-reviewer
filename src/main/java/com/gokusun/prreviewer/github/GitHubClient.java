package com.gokusun.prreviewer.github;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** GitHub REST API 客户端，持有拉取 diff 与读写 JSON 两个连接。 */
@Component
public class GitHubClient {

  private static final String DEFAULT_BASE_URL = "https://api.github.com";

  private final GitHubProperties props;
  private final RestClient diffHttp;
  private final RestClient jsonHttp;

  public GitHubClient(GitHubProperties props) {
    this(props, DEFAULT_BASE_URL);
  }

  GitHubClient(GitHubProperties props, String baseUrl) {
    this.props = props;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(15));
    this.diffHttp = client(factory, baseUrl, "application/vnd.github.v3.diff");
    this.jsonHttp = client(factory, baseUrl, "application/vnd.github+json");
  }

  /**
   * 按 Accept 造一个带鉴权与超时的客户端。
   *
   * @param factory 连接工厂，两个客户端共用同一份超时配置
   * @param baseUrl API 根地址
   * @param accept  Accept 头取值
   * @return 固定了 baseUrl / 超时 / Accept / Authorization 的客户端
   */
  private RestClient client(
      SimpleClientHttpRequestFactory factory, String baseUrl, String accept) {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader("Accept", accept)
        .defaultHeader("Authorization", "Bearer " + props.getToken())
        .build();
  }

  /**
   * 拉取 PR 的 diff 文本。
   *
   * @param owner  仓库所有者
   * @param repo   仓库名
   * @param number PR 编号
   * @return diff 原文
   * @throws org.springframework.web.client.RestClientResponseException 服务器返回 4xx
   *                                                                    / 5xx
   */
  public String pullDiff(String owner, String repo, int number) {
    return diffHttp.get()
        .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
        .retrieve()
        .body(String.class);
  }

  /**
   * 读取 PR 当前 head commit 的 SHA。
   *
   * @param owner  仓库所有者
   * @param repo   仓库名
   * @param number PR 编号
   * @return PR 的 head 坐标
   * @throws org.springframework.web.client.RestClientResponseException 服务器返回 4xx
   *                                                                    / 5xx
   */
  public PullRequestMeta prMeta(String owner, String repo, int number) {
    PrResponse response = jsonHttp
        .get()
        .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
        .retrieve()
        .body(PrResponse.class);
    return new PullRequestMeta(response.head().sha());
  }

  /**
   * 在 PR 上创建一条行内评论。
   *
   * @param owner   仓库所有者
   * @param repo    仓库名
   * @param number  PR 编号
   * @param comment 评论坐标与正文
   * @return 新建评论的 id
   * @throws org.springframework.web.client.RestClientResponseException 服务器返回 4xx
   *                                                                    / 5xx
   */
  public long createReviewComment(String owner, String repo, int number, NewReviewComment comment) {
    CreatedComment created = jsonHttp
        .post()
        .uri("/repos/{owner}/{repo}/pulls/{number}/comments", owner, repo, number)
        .body(comment)
        .retrieve()
        .body(CreatedComment.class);
    return created.id();
  }

  record PrResponse(PrHead head) {
  }

  record PrHead(String sha) {
  }

  record CreatedComment(long id) {
  }
}
