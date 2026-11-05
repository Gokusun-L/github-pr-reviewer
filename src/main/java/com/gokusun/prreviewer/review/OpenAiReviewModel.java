package com.gokusun.prreviewer.review;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 唯一碰网络的实现。只负责"发出去、把原话拿回来"，不判断成败。 */
@Component
public class OpenAiReviewModel implements ReviewModel {

  private final OpenAiChatModel chatModel;

  public OpenAiReviewModel(OpenAiApi api, OpenAiChatOptions options) {
    this.chatModel =
        OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            // 这一行不许删、不许注释：不写不报错，框架静默填 maxAttempts(10)
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
  }

  @Override
  public String invoke(String systemPrompt, String userPrompt) {
    Prompt prompt =
        new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
    return chatModel.call(prompt).getResult().getOutput().getText();
  }
}
