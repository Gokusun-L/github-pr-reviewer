package com.gokusun.prreviewer;

import com.gokusun.prreviewer.github.GitHubProperties;
import com.gokusun.prreviewer.review.DeepSeekProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GitHubProperties.class, DeepSeekProperties.class})
public class PrReviewerApplication {

  public static void main(String[] args) {
    SpringApplication.run(PrReviewerApplication.class, args);
  }
}