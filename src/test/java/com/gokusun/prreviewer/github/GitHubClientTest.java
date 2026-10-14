package com.gokusun.prreviewer.github;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

/** D2 验收：能拉到真实 PR 的 diff 文本。 */
@SpringBootTest
class GitHubClientTest {

  @Autowired private GitHubClient gitHubClient;

  @Value("${github.token:}")
  private String token;

  @Test
  void pullDiff_shouldReturnRealUnifiedDiff() {
    assumeTrue(token != null && !token.isBlank(), "未配置 GITHUB_TOKEN，跳过真实调用");

    String diff;
    try {
      diff = gitHubClient.pullDiff("Gokusun-L", "github-pr-reviewer", 1);
    } catch (ResourceAccessException e) {
      throw new TestAbortedException("JVM 无法访问 GitHub，跳过真实调用: " + e.getMessage(), e);
    }

    System.out.println("========== PR #1 diff ==========");
    System.out.println(diff);
    assertNotNull(diff);
    assertTrue(diff.contains("diff --git"), "应返回 unified diff 文本");
  }
}