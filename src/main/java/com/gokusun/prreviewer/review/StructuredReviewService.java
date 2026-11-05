package com.gokusun.prreviewer.review;

import com.gokusun.prreviewer.diff.DiffFile;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * S7 的编排：拼 prompt → 调模型 → 解析 → 校验，外加重试与失败分类。
 *
 * <p>不碰 Spring：外部依赖全部构造器注入，所以能脱上下文单测。
 */
public final class StructuredReviewService {

  /** 首次 + 重试 2 次。这个数字是契约，不是魔法数。 */
  static final int MAX_ATTEMPTS = 3;

  /** 排障用的原话长度上限。 */
  private static final int RAW_SNIPPET_LENGTH = 200;

  private final ReviewModel model;
  private final BeanOutputConverter<LlmReview> converter;
  private final ReviewPromptBuilder promptBuilder;
  private final FindingValidator validator;
  private final List<Duration> backoff;
  private final Sleeper sleeper;

  public StructuredReviewService(
      ReviewModel model,
      BeanOutputConverter<LlmReview> converter,
      ReviewPromptBuilder promptBuilder,
      FindingValidator validator,
      List<Duration> backoff,
      Sleeper sleeper) {
    this.model = Objects.requireNonNull(model, "model");
    this.converter = Objects.requireNonNull(converter, "converter");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.backoff = List.copyOf(Objects.requireNonNull(backoff, "backoff"));
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    if (this.backoff.size() != MAX_ATTEMPTS - 1) {
      throw new IllegalArgumentException(
          "backoff 长度必须是 " + (MAX_ATTEMPTS - 1) + "，实际 " + this.backoff.size());
    }
  }

  public ReviewResult review(List<DiffFile> files) {
    BuiltPrompt prompt = promptBuilder.build(files);
    if (prompt.nothingToReview()) {
      return new ReviewResult(List.of(), List.of(), List.of(), 0);
    }

    String systemPrompt = prompt.systemPrompt() + "\n\n" + converter.getFormat();

    LlmReview review = null;
    ReviewFailureKind kind = null;
    String rawSnippet = null;
    Throwable lastFailure = null;
    int attempts = 0;

    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      if (i > 0) {
        sleeper.sleep(backoff.get(i - 1));
      }
      attempts = i + 1;

      String raw;
      try {
        raw = model.invoke(systemPrompt, prompt.userPrompt());
      } catch (RuntimeException e) {
        kind = ReviewFailureKind.CALL_FAILED;
        rawSnippet = null;
        lastFailure = e;
        continue;
      }

      if (raw == null || raw.isBlank()) {
        kind = ReviewFailureKind.PARSE_FAILED;
        rawSnippet = null;
        lastFailure = null;
        continue;
      }

      try {
        review = converter.convert(raw);
        break;
      } catch (RuntimeException e) {
        kind = ReviewFailureKind.PARSE_FAILED;
        rawSnippet = snippetOf(raw);
        lastFailure = e;
      }
    }

    if (review == null) {
      throw new ReviewFailedException(
          kind,
          attempts,
          rawSnippet,
          "模型审查失败：kind=" + kind + "，已尝试 " + attempts + " 次",
          lastFailure);
    }

    ValidationOutcome outcome = validator.validate(files, review.findings());
    return new ReviewResult(
        outcome.kept(), outcome.rejected(), prompt.skippedFiles(), attempts);
  }

  private static String snippetOf(String raw) {
    return raw.length() <= RAW_SNIPPET_LENGTH ? raw : raw.substring(0, RAW_SNIPPET_LENGTH);
  }

  /** 退避的注入口。测试传记录器，生产传 Thread::sleep 的包装。 */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration);
  }
}
