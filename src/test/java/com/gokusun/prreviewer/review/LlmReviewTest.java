package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

/** 钉住 S7 契约 K1 / K2 / K3：结构可被 converter 读出、空回答归一、非法 severity 不静默降级。 */
class LlmReviewTest {

  private static final BeanOutputConverter<LlmReview> CONVERTER =
      new BeanOutputConverter<>(LlmReview.class);

  @Test
  void emptyObjectYieldsNullSafeEmptyFindings() {
    // K1：模型回 {} 是"没问题"的合法回答，不能被当成失败，也不能留下 null
    LlmReview review = CONVERTER.convert("{}");

    assertNotNull(review);
    assertNotNull(review.findings());
    assertTrue(review.findings().isEmpty());
  }

  @Test
  void formatTellsTheModelEveryLegalSeverity() {
    // K2 的工具面：模型只可能从 getFormat() 里知道合法取值；少一个就等于这个值不可用
    String format = CONVERTER.getFormat();

    assertFalse(format.isBlank());
    assertTrue(format.contains("severity"), "schema 里必须出现 severity 字段");
    for (Severity severity : Severity.values()) {
      assertTrue(format.contains(severity.name()), "schema 里缺少合法取值 " + severity);
    }
  }

  @Test
  void findingHasExactlyFiveFlatComponentsWithoutSide() {
    // K2：坐标只有"新侧"一种语义；多一个 side 字段就等于让模型自选坐标系
    RecordComponent[] components = Finding.class.getRecordComponents();
    List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();

    assertEquals(
        List.of("filePath", "line", "severity", "message", "suggestion"),
        names,
        "Finding 只允许这 5 个扁平字段，且顺序即 schema 顺序");
  }

  @Test
  void undeclaredSeverityIsRejectedInsteadOfDowngraded() {
    // K3：这条约束由 Jackson 的枚举反序列化实现，不是本项目的代码。
    // 谁把 ObjectMapper 配成宽容模式（未知值当 null），这里就会红在"根本没抛出"。
    String json =
        """
        {"findings":[{"filePath":"src/A.java","line":2,"severity":"HIGH","message":"x"}]}
        """;

    assertThrows(RuntimeException.class, () -> CONVERTER.convert(json));
  }

  @Test
  void declaredSeveritiesRoundTrip() {
    String json =
        """
        {"findings":[
          {"filePath":"a","line":1,"severity":"BLOCKER","message":"m1"},
          {"filePath":"a","line":2,"severity":"MAJOR","message":"m2"},
          {"filePath":"a","line":3,"severity":"MINOR","message":"m3","suggestion":"s3"}
        ]}
        """;

    LlmReview review = CONVERTER.convert(json);

    assertEquals(3, review.findings().size());
    assertEquals(Severity.BLOCKER, review.findings().get(0).severity());
    assertEquals(Severity.MAJOR, review.findings().get(1).severity());
    assertEquals(Severity.MINOR, review.findings().get(2).severity());
    assertEquals("s3", review.findings().get(2).suggestion());
  }
}
