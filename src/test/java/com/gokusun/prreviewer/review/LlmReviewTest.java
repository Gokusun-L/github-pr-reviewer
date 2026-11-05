package com.gokusun.prreviewer.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gokusun.prreviewer.diff.AnnotatedLine;
import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.Hunk;
import com.gokusun.prreviewer.diff.LineType;
import com.gokusun.prreviewer.diff.UnifiedDiffParser;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

/** 钉住 S7 契约 K1 / K2 / K3：结构可被 converter 读出、空回答归一、非法 severity 降级成单条丢弃。 */
class LlmReviewTest {

  /** 生产与测试共用同一个 mapper（ReviewJson），否则测的不是生产那个。 */
  private static final BeanOutputConverter<LlmReview> CONVERTER = ReviewJson.converter();

  private static final String PR1_PATH = "src/main/java/demo/Calculator.java";

  @Test
  void emptyObjectYieldsNullSafeEmptyFindings() {
    // 模型回 {} 是"没问题"的合法回答，不能被当成失败，也不能留下 null
    LlmReview review = CONVERTER.convert("{}");

    assertNotNull(review);
    assertNotNull(review.findings());
    assertTrue(review.findings().isEmpty());
  }

  @Test
  void formatTellsTheModelEveryLegalSeverity() {
    // 模型只可能从 getFormat() 里知道合法取值；少一个就等于这个值不可用
    String format = CONVERTER.getFormat();

    assertFalse(format.isBlank());
    assertTrue(format.contains("severity"), "schema 里必须出现 severity 字段");
    for (Severity severity : Severity.values()) {
      assertTrue(format.contains(severity.name()), "schema 里缺少合法取值 " + severity);
    }
  }

  @Test
  void findingHasExactlyFiveFlatComponentsWithoutSide() {
    // 坐标只有"新侧"一种语义；多一个 side 字段就等于让模型自选坐标系
    RecordComponent[] components = Finding.class.getRecordComponents();
    List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();

    assertEquals(
        List.of("filePath", "line", "severity", "message", "suggestion"),
        names,
        "Finding 只允许这 5 个扁平字段，且顺序即 schema 顺序");
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

  @Test
  void unknownSeverityIsDroppedNotFatal() throws IOException {
    // 模型写 HIGH：宽容 mapper 把它变成 null，由校验器判 MISSING_FIELD 只丢这一条，
    // 同批另外两条合法 finding 必须保住。
    List<DiffFile> files = parse("pr1.diff");
    List<Integer> addedLines = addedNewLines(files, 3);
    String json =
        """
        {"findings":[
          {"filePath":"%s","line":%d,"severity":"HIGH","message":"m1"},
          {"filePath":"%s","line":%d,"severity":"MAJOR","message":"m2"},
          {"filePath":"%s","line":%d,"severity":"MINOR","message":"m3"}
        ]}
        """
            .formatted(
                PR1_PATH,
                addedLines.get(0),
                PR1_PATH,
                addedLines.get(1),
                PR1_PATH,
                addedLines.get(2));

    LlmReview review = CONVERTER.convert(json);

    assertEquals(3, review.findings().size(), "整批不能被一条坏值带走");
    assertNull(review.findings().get(0).severity(), "未知枚举值应降级成 null");

    ValidationOutcome outcome = new FindingValidator().validate(files, review.findings());
    assertEquals(2, outcome.kept().size());
    assertEquals(1, outcome.rejected().size());
    assertEquals(FindingRejectReason.MISSING_FIELD, outcome.rejected().get(0).reason());
  }

  private static List<Integer> addedNewLines(List<DiffFile> files, int howMany) {
    List<Integer> lines = new ArrayList<>();
    for (DiffFile file : files) {
      for (Hunk hunk : file.hunks()) {
        for (AnnotatedLine line : hunk.annotated()) {
          if (line.line().type() == LineType.ADDED && line.newLine() > 0) {
            lines.add(line.newLine());
          }
        }
      }
    }
    if (lines.size() < howMany) {
      throw new AssertionError("样本里 ADDED 行不足 " + howMany + " 条");
    }
    return lines;
  }

  private static List<DiffFile> parse(String name) throws IOException {
    try (InputStream in = LlmReviewTest.class.getResourceAsStream("/diffs/" + name)) {
      if (in == null) {
        throw new IOException("classpath 下找不到 /diffs/" + name);
      }
      return new UnifiedDiffParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }
}
