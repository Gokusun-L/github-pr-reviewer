package com.gokusun.prreviewer.review;

import com.gokusun.prreviewer.diff.AnnotatedLine;
import com.gokusun.prreviewer.diff.DiffFile;
import com.gokusun.prreviewer.diff.Hunk;
import com.gokusun.prreviewer.diff.LineType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 List&lt;DiffFile&gt; 渲染成带真实行号的 prompt，并按字符预算裁剪。
 *
 * <p>为什么不用 .st 模板引擎：逐行代码块是程序生成的，模板层只承担"字符串塞占位符"，
 * 却引入 { 转义、缺失变量静默成空串这类只在生产暴露的失败面。
 */
public final class ReviewPromptBuilder {

  private static final String SYSTEM_PROMPT_RESOURCE = "/prompts/review-system.md";

  private final String systemPrompt;
  private final int maxUserChars;

  public ReviewPromptBuilder(int maxUserChars) {
    if (maxUserChars <= 0) {
      throw new IllegalArgumentException("maxUserChars 必须为正数: " + maxUserChars);
    }
    this.maxUserChars = maxUserChars;
    this.systemPrompt = loadSystemPrompt();
  }

  public BuiltPrompt build(List<DiffFile> files) {
    List<String> renderedFiles = new ArrayList<>();
    List<String> skippedFiles = new ArrayList<>();
    List<DiffFile> reviewable = new ArrayList<>();
    for (DiffFile file : files) {
      if (file.newPath() == null || file.hunks().isEmpty()) {
        continue;
      }
      reviewable.add(file);
    }

    StringBuilder userPrompt = new StringBuilder();
    for (DiffFile file : reviewable) {
      String block = render(file);
      if (userPrompt.length() + block.length() <= maxUserChars) {
        userPrompt.append(block);
        renderedFiles.add(file.newPath());
      } else {
        skippedFiles.add(file.newPath());
      }
    }

    if (!reviewable.isEmpty() && renderedFiles.isEmpty()) {
      throw new ReviewFailedException(
          ReviewFailureKind.BUDGET_EXHAUSTED,
          0,
          null,
          "有 " + reviewable.size() + " 个可审文件，但一个都放不进预算 " + maxUserChars,
          null);
    }

    return new BuiltPrompt(systemPrompt, userPrompt.toString(), renderedFiles, skippedFiles);
  }

  private static String render(DiffFile file) {
    StringBuilder block = new StringBuilder();
    block.append("### FILE ").append(file.newPath()).append('\n');
    for (Hunk hunk : file.hunks()) {
      for (AnnotatedLine line : hunk.annotated()) {
        String text = stripOneTrailingCr(line.line().content());
        LineType type = line.line().type();
        if (type == LineType.ADDED) {
          block.append(String.format("%4d + %s", line.newLine(), text));
        } else if (type == LineType.CONTEXT) {
          block.append(String.format("%4d   %s", line.newLine(), text));
        } else {
          block.append(String.format("%4s - %s", "-", text));
        }
        block.append('\n');
      }
    }
    return block.toString();
  }

  /** 只去掉行尾的**一个** \r；数据模型里的 \r 是内容，不在这里之外剥除。 */
  private static String stripOneTrailingCr(String text) {
    if (text.endsWith("\r")) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  private static String loadSystemPrompt() {
    try (InputStream in = ReviewPromptBuilder.class.getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("classpath 下找不到 " + SYSTEM_PROMPT_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取系统提示词失败: " + SYSTEM_PROMPT_RESOURCE, e);
    }
  }
}
