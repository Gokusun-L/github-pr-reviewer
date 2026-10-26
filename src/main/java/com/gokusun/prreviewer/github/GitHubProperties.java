package com.gokusun.prreviewer.github;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

  private String token;

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @PostConstruct
  void validate() {
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("github.token 未配置，检查仓库根 .env 的 GITHUB_TOKEN");
    }
    if (token.startsWith("${")) {
      throw new IllegalStateException("github.token 是未解析的占位符，检查 .env 是否被加载");
    }
  }
}
