package com.gokusun.prreviewer.review;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * 全项目唯一的 JSON 入口：schema 生成和反序列化共用同一个 mapper。
 *
 * <p>为什么宽容未知枚举值：模型写 HIGH / 高 / CRITICAL 是常态，
 * 严格模式下一条坏值会让整份响应解析失败，同批合法 finding 全部陪葬。
 * 宽容后它变成 severity=null，交给 FindingValidator 判 MISSING_FIELD，只丢这一条。
 */
public final class ReviewJson {

  private ReviewJson() {}

  public static ObjectMapper mapper() {
    return JsonMapper.builder()
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .build();
  }

  public static BeanOutputConverter<LlmReview> converter() {
    return new BeanOutputConverter<>(LlmReview.class, mapper());
  }
}
