package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 用本地 HTTP 服务数真实请求次数，证明内层 RetryTemplate 只尝试 1 次。
 *
 * <p>stub 模型只能数外层 for 循环；内层被改回 maxAttempts(10) 时，
 * stub 仍然只会看到 3 次调用，所以这条判据必须由服务端计数。
 */
class OpenAiReviewModelHttpTest {

  private final AtomicInteger serverHits = new AtomicInteger();
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          serverHits.incrementAndGet();
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void retryCountIsVerifiedByRealServerHits() throws IOException {
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl("http://localhost:" + server.getAddress().getPort())
            .apiKey(new SimpleApiKey("test-key"))
            .build();
    ReviewModel model =
        new OpenAiReviewModel(api, OpenAiChatOptions.builder().model("test-model").build());

    StructuredReviewService service =
        new StructuredReviewService(
            model,
            ReviewJson.converter(),
            new ReviewPromptBuilder(20_000),
            new FindingValidator(),
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(3)),
            duration -> {});

    ReviewFailedException thrown =
        assertThrows(ReviewFailedException.class, () -> service.review(parse("pr1.diff")));

    assertEquals(ReviewFailureKind.CALL_FAILED, thrown.kind());
    assertEquals(3, thrown.attempts(), "外层：首次 + 重试 2 次");
    assertEquals(3, serverHits.get(), "内层 RetryTemplate 必须是 maxAttempts(1)");
  }

  private static List<DiffFile> parse(String name) throws IOException {
    try (InputStream in = OpenAiReviewModelHttpTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new UnifiedDiffParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
