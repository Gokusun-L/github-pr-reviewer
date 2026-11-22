package com.gokusun.prreviewer.github;

import com.gokusun.prreviewer.review.Finding;
import com.gokusun.prreviewer.review.ReviewResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/** 把一次审查保留的发现逐条发成 PR 行内评论。 */
@Component
public class CommentPublisher {

  private final GitHubClient client;

  public CommentPublisher(GitHubClient client) {
    this.client = client;
  }

  /**
   * 逐条发布评论；单条被服务器拒绝时只计数，不中断后续。
   *
   * @param owner  仓库所有者
   * @param repo   仓库名
   * @param number PR 编号
   * @param review 一次审查的产物；只发 {@code findings()}，不碰 {@code rejected()}
   * @return 成功与失败的条数
   * @throws org.springframework.web.client.RestClientResponseException 读 head SHA 失败时整批没有
   *     可用的 commit_id，异常向上抛
   */
  public PublishReport publish(String owner, String repo, int number, ReviewResult review) {
    if (review.findings().isEmpty()) {
      return new PublishReport(0, 0);
    }

    String headSha = client.prMeta(owner, repo, number).headSha();
    int posted = 0;
    int failed = 0;
    for (Finding finding : review.findings()) {
      NewReviewComment comment =
          new NewReviewComment(
              format(finding),
              headSha,
              finding.filePath(),
              finding.line(),
              NewReviewComment.SIDE_RIGHT);
      try {
        client.createReviewComment(owner, repo, number, comment);
        posted++;
      } catch (RestClientResponseException e) {
        failed++;
      }
    }
    return new PublishReport(posted, failed);
  }

  /** 把一条发现拼成评论正文。 */
  static String format(Finding finding) {
    StringBuilder text = new StringBuilder();
    text.append("**").append(finding.severity()).append("** ").append(finding.message());
    if (finding.suggestion() != null && !finding.suggestion().isBlank()) {
      text.append("\n\n建议：").append(finding.suggestion());
    }
    return text.toString();
  }
}
