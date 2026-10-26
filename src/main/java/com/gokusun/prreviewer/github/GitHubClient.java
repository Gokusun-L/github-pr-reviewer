package com.gokusun.prreviewer.github;

import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.Hunk;
import com.gokusun.prreviewer.diff.LineType;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubClient implements ApplicationRunner {

  private final GitHubProperties props;
  private final RestClient http;
  private final UnifiedDiffParser parser = new UnifiedDiffParser();

  public GitHubClient(GitHubProperties props) {
    this.props = props;
    this.http =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github.v3.diff")
            .build();
  }

  @Override
  public void run(ApplicationArguments args) {
    String diff = pullDiff("Gokusun-L", "github-pr-reviewer", 1);
    if (diff == null || diff.isBlank()) {
      return;
    }

    List<DiffFile> files = parser.parse(diff);
    System.out.println("files=" + files.size());
    for (DiffFile file : files) {
      String name = file.displayName();
      String shortName = name.substring(name.lastIndexOf('/') + 1);
      System.out.println("  " + shortName + "  hunks=" + file.hunks().size());
      for (Hunk hunk : file.hunks()) {
        System.out.printf(
            "    @@ -%d,%d +%d,%d @@  context=%d added=%d removed=%d noNewline=%d%n",
            hunk.oldStart(),
            hunk.oldCount(),
            hunk.newStart(),
            hunk.newCount(),
            count(hunk, LineType.CONTEXT),
            count(hunk, LineType.ADDED),
            count(hunk, LineType.REMOVED),
            count(hunk, LineType.NO_NEWLINE));
      }
    }
  }

  public String pullDiff(String owner, String repo, int number) {
    return http.get()
        .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
        .header("Authorization", "Bearer " + props.getToken())
        .retrieve()
        .body(String.class);
  }

  private static long count(Hunk hunk, LineType type) {
    return hunk.lines().stream().filter(line -> line.type() == type).count();
  }
}
