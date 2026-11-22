package com.gokusun.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class GitHubClientWiringTest {

  @Test
  void clientIsInstantiatedByContainer() {
    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
      GitHubProperties props = new GitHubProperties();
      props.setToken("t");
      ctx.registerBean(GitHubProperties.class, () -> props);
      ctx.register(GitHubClient.class, CommentPublisher.class);
      ctx.refresh();

      assertThat(ctx.getBean(GitHubClient.class)).isNotNull();
      assertThat(ctx.getBean(CommentPublisher.class)).isNotNull();
    }
  }
}
