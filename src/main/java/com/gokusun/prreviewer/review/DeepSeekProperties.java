package com.gokusun.prreviewer.review;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消费 application.yml 里的 spring.ai.openai.*。
 *
 * <p>不加 {@code @Component}：靠启动类的 {@code @EnableConfigurationProperties} 注册，
 * 与 GitHubProperties 同一写法（同时加会注册两个同类型 bean）。
 */
@ConfigurationProperties(prefix = "spring.ai.openai")
public class DeepSeekProperties {

  private String baseUrl;
  private String apiKey;
  private final Chat chat = new Chat();

  public static class Chat {
    private final Options options = new Options();

    public Options getOptions() {
      return options;
    }
  }

  public static class Options {
    private String model;
    private Double temperature;

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }
  }

  /** 配置写错时启动即失败，而不是拿框架默认值跑。 */
  @PostConstruct
  void check() {
    String model = chat.getOptions().getModel();
    if (model == null || model.isBlank()) {
      throw new IllegalStateException(
          "spring.ai.openai.chat.options.model 不能为空，检查 .env / application.yml");
    }
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public Chat getChat() {
    return chat;
  }
}
