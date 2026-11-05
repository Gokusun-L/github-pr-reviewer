package com.gokusun.prreviewer.review;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把纯 Java 的组件装配成 bean；网络与模型参数只在这里落地。 */
@Configuration
public class ReviewConfig {

  @Bean
  OpenAiApi openAiApi(DeepSeekProperties properties) {
    // 空串可以（缺 key 时只在调用时报错），null 会抛 apiKey cannot be null
    String key = properties.getApiKey() == null ? "" : properties.getApiKey();
    return OpenAiApi.builder().baseUrl(properties.getBaseUrl()).apiKey(new SimpleApiKey(key)).build();
  }

  @Bean
  OpenAiChatOptions openAiChatOptions(DeepSeekProperties properties) {
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder().model(properties.getChat().getOptions().getModel());
    Double temperature = properties.getChat().getOptions().getTemperature();
    if (temperature != null) {
      builder.temperature(temperature);
    }
    return builder.build();
  }

  @Bean
  BeanOutputConverter<LlmReview> llmReviewConverter() {
    return ReviewJson.converter();
  }

  @Bean
  ReviewPromptBuilder reviewPromptBuilder() {
    return new ReviewPromptBuilder(20_000);
  }

  @Bean
  FindingValidator findingValidator() {
    return new FindingValidator();
  }

  @Bean
  StructuredReviewService structuredReviewService(
      ReviewModel model,
      BeanOutputConverter<LlmReview> converter,
      ReviewPromptBuilder promptBuilder,
      FindingValidator validator) {
    return new StructuredReviewService(
        model,
        converter,
        promptBuilder,
        validator,
        List.of(Duration.ofSeconds(1), Duration.ofSeconds(3)),
        ReviewConfig::sleepUninterruptibly);
  }

  private static void sleepUninterruptibly(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ReviewFailedException(
          ReviewFailureKind.CALL_FAILED, 0, null, "重试等待被中断", e);
    }
  }
}
