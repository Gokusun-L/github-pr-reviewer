package com.gokusun.prreviewer.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.gokusun.prreviewer.github.GitHubClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

/** 用真实 PR diff 校验行号映射。 */
@SpringBootTest
class UnifiedDiffParserRealPrTest {

  @Autowired private GitHubClient gitHubClient;
  @Autowired private UnifiedDiffParser parser;

  @Value("${github.token:}")
  private String token;

  @Test
  void realPrDiff_mapsAddedLinesToGitHubLineNumbers() {
    assumeTrue(token != null && !token.isBlank(), "未配置 GITHUB_TOKEN，跳过真实调用");

    String diff;
    try {
      diff = gitHubClient.pullDiff("Gokusun-L", "github-pr-reviewer", 1);
    } catch (ResourceAccessException e) {
      throw new TestAbortedException("JVM 无法访问 GitHub，跳过真实调用: " + e.getMessage(), e);
    }
    List<FileDiff> files = parser.parse(diff);

    FileDiff calculator =
        files.stream()
            .filter(f -> f.path().equals("src/main/java/demo/Calculator.java"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未解析到 Calculator.java，实际文件: " + files.stream().map(FileDiff::path).toList()));

    int apiKeyLine = addedLineNo(calculator, "API_KEY");
    int divideLine = addedLineNo(calculator, "a / b");

    System.out.println("API_KEY -> 新文件第 " + apiKeyLine + " 行");
    System.out.println("a / b   -> 新文件第 " + divideLine + " 行");

    assertEquals(5, apiKeyLine);
    assertEquals(13, divideLine);
  }

  private static int addedLineNo(FileDiff file, String snippet) {
    return file.hunks().stream()
        .flatMap(hunk -> hunk.lines().stream())
        .filter(line -> line.type() == ChangeType.ADDED && line.content().contains(snippet))
        .map(ChangedLine::newLineNo)
        .findFirst()
        .orElseThrow(() -> new AssertionError("未找到包含 " + snippet + " 的新增行"));
  }
}