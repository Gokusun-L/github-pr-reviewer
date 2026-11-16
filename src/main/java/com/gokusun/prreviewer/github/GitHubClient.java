package com.gokusun.prreviewer.github;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubClient {

  private final GitHubProperties props;
  private final RestClient http;

  public GitHubClient(GitHubProperties props) {
    this.props = props;
    this.http =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github.v3.diff")
            .build();
  }

  public String pullDiff(String owner, String repo, int number) {
    return http.get()
        .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
        .header("Authorization", "Bearer " + props.getToken())
        .retrieve()
        .body(String.class);
  }
}
