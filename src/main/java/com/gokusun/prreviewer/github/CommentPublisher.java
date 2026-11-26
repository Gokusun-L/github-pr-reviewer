package com.gokusun.prreviewer.github;

import com.gokusun.prreviewer.review.Finding;
import com.gokusun.prreviewer.review.RetryDecision;
import com.gokusun.prreviewer.review.ReviewResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 把一次审查保留的发现逐条发成 PR 行内评论。 */
@Component
public class CommentPublisher {

  private final GitHubClient client;

  public CommentPublisher(GitHubClient client) {
    this.client = client;
  }

  /**
   * 逐条发布评论；单条被服务器拒绝时只记录坐标，不中断后续。
   *
   * @param owner  仓库所有者
   * @param repo   仓库名
   * @param number PR 编号
   * @param review 一次审查的产物；只发 {@code findings()}，不碰 {@code rejected()}
   * @return 成功条数与失败明细
   * @throws org.springframework.web.client.RestClientResponseException 读 head SHA 失败时整批没有
   *     可用的 commit_id，异常向上抛
   * @throws org.springframework.web.client.ResourceAccessException 读 head SHA 时连不上服务器，同上
   */
  public PublishReport publish(String owner, String repo, int number, ReviewResult review) {
    if (review.findings().isEmpty()) {
      return new PublishReport(0, List.of());
    }

    String headSha = client.prMeta(owner, repo, number).headSha();
    int posted = 0;
    List<PublishReport.Failure> failures = new ArrayList<>();
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
      } catch (RestClientException e) {
        Integer status =
            e instanceof RestClientResponseException response
                ? response.getStatusCode().value()
                : null;
        failures.add(
            new PublishReport.Failure(
                finding.filePath(), finding.line(), status, RetryDecision.retryable(e)));
      }
    }
    return new PublishReport(posted, failures);
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
