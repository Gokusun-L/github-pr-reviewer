package com.gokusun.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokusun.prreviewer.review.Finding;
import com.gokusun.prreviewer.review.FindingRejectReason;
import com.gokusun.prreviewer.review.RejectedFinding;
import com.gokusun.prreviewer.review.ReviewResult;
import com.gokusun.prreviewer.review.Severity;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommentPublisherTest {

  private HttpServer server;
  private final AtomicInteger pullHits = new AtomicInteger();
  private final AtomicInteger postHits = new AtomicInteger();

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void secondCommentRejectedKeepsPublishingTheRest() throws IOException {
    server = startServer(2);

    PublishReport report = publisherFor(server).publish("o", "r", 1, result(4, List.of()));

    assertThat(report.posted()).isEqualTo(3);
    assertThat(report.failed()).isEqualTo(1);
    assertThat(report.failures())
        .containsExactly(new PublishReport.Failure("A.java", 2, 422, false));
    assertThat(postHits.get()).isEqualTo(4);
    assertThat(pullHits.get()).isEqualTo(1);
  }

  @Test
  void serverErrorOnOneCommentIsMarkedRetryable() throws IOException {
    server = startServer(1, 502, -1);

    PublishReport report = publisherFor(server).publish("o", "r", 1, result(2, List.of()));

    assertThat(report.posted()).isEqualTo(1);
    assertThat(report.failures())
        .containsExactly(new PublishReport.Failure("A.java", 1, 502, true));
  }

  @Test
  void networkFailureOnOneCommentHasNoStatusAndIsRetryable() throws IOException {
    server = startServer(0, 422, 2);

    PublishReport report = publisherFor(server).publish("o", "r", 1, result(4, List.of()));

    assertThat(report.posted()).isEqualTo(3);
    assertThat(report.failures())
        .containsExactly(new PublishReport.Failure("A.java", 2, null, true));
  }

  @Test
  void rejectedFindingsAreNeverPosted() throws IOException {
    server = startServer(0);

    PublishReport report = publisherFor(server).publish("o", "r", 1, result(2, rejectedOne()));

    assertThat(report.posted()).isEqualTo(2);
    assertThat(report.failed()).isZero();
    assertThat(postHits.get()).isEqualTo(2);
  }

  @Test
  void emptyKeptListSendsNothing() throws IOException {
    server = startServer(0);

    PublishReport report = publisherFor(server).publish("o", "r", 1, result(0, rejectedOne()));

    assertThat(report.posted()).isZero();
    assertThat(report.failed()).isZero();
    assertThat(pullHits.get()).isZero();
    assertThat(postHits.get()).isZero();
  }

  private static CommentPublisher publisherFor(HttpServer server) {
    GitHubProperties props = new GitHubProperties();
    props.setToken("test-token");
    return new CommentPublisher(
        new GitHubClient(props, "http://127.0.0.1:" + server.getAddress().getPort()));
  }

  /** 造一次审查产物：kept 条正常发现 + 指定的被拒条目。 */
  private static ReviewResult result(int keptCount, List<RejectedFinding> rejected) {
    List<Finding> findings = new ArrayList<>();
    for (int i = 1; i <= keptCount; i++) {
      findings.add(new Finding("A.java", i, Severity.MAJOR, "问题 " + i, "建议 " + i));
    }
    return new ReviewResult(findings, rejected, List.of(), 1);
  }

  /** 被拒条目：line 为 null，若被误发会在拆箱处炸。 */
  private static List<RejectedFinding> rejectedOne() {
    Finding dropped = new Finding("A.java", null, Severity.MINOR, "缺行号", null);
    return List.of(new RejectedFinding(dropped, FindingRejectReason.MISSING_FIELD));
  }

  /**
   * 假 API：第 N 次 POST 返回 422，其余成功。
   *
   * @param rejectPostNumber 第几次 POST 返回 422；0 表示全部成功
   */
  private HttpServer startServer(int rejectPostNumber) throws IOException {
    return startServer(rejectPostNumber, 422, -1);
  }

  /**
   * 假 API：同一路径按 Accept 与方法分支。
   *
   * @param rejectPostNumber 第几次 POST 返回 rejectStatus；0 表示不拒绝
   * @param rejectStatus     拒绝时返回的状态码
   * @param dropPostNumber   第几次 POST 直接断开连接；-1 表示不断
   */
  private HttpServer startServer(int rejectPostNumber, int rejectStatus, int dropPostNumber)
      throws IOException {
    HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    created.createContext(
        "/repos/o/r/pulls/1",
        exchange -> {
          if (exchange.getRequestURI().getPath().endsWith("/comments")) {
            int nth = postHits.incrementAndGet();
            if (nth == dropPostNumber) {
              exchange.close();
              return;
            }
            if (nth == rejectPostNumber) {
              respond(exchange, rejectStatus, "application/json", "{\"message\":\"Validation Failed\"}");
            } else {
              respond(exchange, 201, "application/json", "{\"id\":" + nth + "}");
            }
            return;
          }
          pullHits.incrementAndGet();
          String accept = exchange.getRequestHeaders().getFirst("Accept");
          if (accept != null && accept.contains("application/vnd.github.v3.diff")) {
            respond(exchange, 200, "text/plain", "diff --git a/A.java b/A.java\n");
          } else {
            respond(exchange, 200, "application/json", "{\"head\":{\"sha\":\"abc123\"}}");
          }
        });
    created.start();
    return created;
  }

  private static void respond(
      com.sun.net.httpserver.HttpExchange exchange, int status, String type, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", type);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
