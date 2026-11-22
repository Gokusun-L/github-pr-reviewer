package com.gokusun.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NewReviewCommentTest {

  @Test
  void sideLowercaseIsRejected() {
    assertThatThrownBy(() -> new NewReviewComment("b", "sha", "A.java", 1, "right"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sideNullIsRejected() {
    assertThatThrownBy(() -> new NewReviewComment("b", "sha", "A.java", 1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
