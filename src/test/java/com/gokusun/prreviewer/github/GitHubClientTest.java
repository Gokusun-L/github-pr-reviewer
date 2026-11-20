package com.gokusun.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

class GitHubClientTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void prMetaReadsHeadShaWithJsonAccept() throws IOException {
    AtomicReference<String> accept = new AtomicReference<>();
    server =
        start(
            "/repos/o/r/pulls/1",
            exchange -> {
              accept.set(exchange.getRequestHeaders().getFirst("Accept"));
              respond(exchange, 200, "{\"head\":{\"sha\":\"abc123\"}}");
            });

    PullRequestMeta meta = clientFor(server).prMeta("o", "r", 1);

    assertThat(meta.headSha()).isEqualTo("abc123");
    assertThat(accept.get()).isEqualTo("application/vnd.github+json");
  }

  @Test
  void createReviewCommentSendsCommitIdAndReturnsCreatedId() throws IOException {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    server =
        start(
            "/repos/o/r/pulls/1/comments",
            exchange -> {
              method.set(exchange.getRequestMethod());
              requestBody.set(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
              respond(exchange, 201, "{\"id\":98765}");
            });

    long id =
        clientFor(server)
            .createReviewComment(
                "o", "r", 1, new NewReviewComment("正文", "abc123", "A.java", 5, "RIGHT"));

    assertThat(id).isEqualTo(98765L);
    assertThat(method.get()).isEqualTo("POST");
    assertThat(requestBody.get()).contains("\"commit_id\":\"abc123\"");
    assertThat(requestBody.get()).contains("\"line\":5");
    assertThat(requestBody.get()).contains("\"side\":\"RIGHT\"");
  }

  @Test
  void validationFailureIsThrownAfterOneRequest() throws IOException {
    AtomicInteger hits = new AtomicInteger();
    server =
        start(
            "/repos/o/r/pulls/1/comments",
            exchange -> {
              hits.incrementAndGet();
              respond(exchange, 422, "{\"message\":\"Validation Failed\"}");
            });

    GitHubClient client = clientFor(server);
    NewReviewComment comment = new NewReviewComment("正文", "abc123", "A.java", 5, "RIGHT");

    assertThatThrownBy(() -> client.createReviewComment("o", "r", 1, comment))
        .isInstanceOf(RestClientResponseException.class);

    assertThat(hits.get()).isEqualTo(1);
  }

  private static GitHubClient clientFor(HttpServer server) {
    GitHubProperties props = new GitHubProperties();
    props.setToken("test-token");
    return new GitHubClient(props, "http://127.0.0.1:" + server.getAddress().getPort());
  }

  private static HttpServer start(String path, HttpHandler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(path, handler);
    server.start();
    return server;
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
