# 代码审查记录（7 个真实缺陷）

> 对象：开源项目 `interview-guide`（AI 模拟面试平台）源码研读。
> 方法：对每处可疑行为提出质疑 → 回源码核实调用链 → 确认为真缺陷才收录；未核实的推测不入册。
> 格式：位置（文件名 + 行号）→ 问题 → 影响 → 修法 → 面试可讲点。
> 用途：① 回答"这个项目有什么不足 / 你会怎么改"；② 本项目设计阶段主动规避同类问题。
> 说明：行号对应研读时的工程版本，跨版本可能漂移。

## 收录条目总览

| # | 模块 | 一句话 | 状态 |
|---|------|--------|------|
| 1 | 面试出题 | BusinessException 绕过兜底题，建会话直接失败 | 🟥 已核实未修 |
| 2 | 面试出题 | 两条降级分支不对称：白扔 40% 调用 / 只给 60% 不补量 | 🟥 已核实未修 |
| 3 | 面试出题 | 题量不足无校验，要 8 题给 3 题系统认为正常 | 🟥 已核实未修 |
| 4 | 面试出题 | 简历题方法缺空题单检查（只有方向题有） | 🟥 已核实未修 |
| 5 | 面试日程 | source（平台名）被当 providerId 传给 LLM 调用 | 🟥 已核实未修 |
| 6 | 简历上传 | 并发唯一约束冲突被统一 catch 成"保存简历失败"，且 RustFS 孤儿文件不清理 | 🟥 已核实未修 |
| 7 | 面试答题 | 答案落库失败被静默吞掉，而评估只读 DB → 该题按"未作答"评分 | 🟥 已核实未修 |

---

## 1. BusinessException 绕过兜底题 🟥

- **位置**：InterviewQuestionService.java L267-L269（方向题）、同文件 L218-L219（简历题同样 rethrow）
- **问题**：`StructuredOutputInvoker` 重试耗尽抛 `BusinessException`；而 `generateDirectionOnly` 里 `catch (BusinessException e) { throw e; }` 排在兜底逻辑**前面**，异常从兜底旁边溜走
- **影响**：两条路径受影响——① 无简历（`createSession` 直调 `generateDirectionOnly`，无任何 try/catch）；② 有简历但简历题挂 → 重跑方向题。这两条路上 LLM 结构化输出耗尽重试 → 异常直穿 `createSession` → **建会话直接失败，兜底题形同虚设**
- **修法**：整个 `generateQuestionsBySkill` 出口统一包一层，`BusinessException` 也落 `generateFallbackQuestions`。理由：兜底题纯内存拼接零 IO、不会失败，它才是真正的终态；"开不了面试"这个后果不该由一次 LLM 调用决定
- **话术**："我的降级只覆盖了'空题单'和'非业务异常'，`BusinessException` 会被 catch 原样放走。兜底题不依赖 LLM、永远不会失败，正确做法是在方法出口统一收口，所有失败最终都落兜底。"

## 2. 两条降级分支不对称 🟥

- **位置**：InterviewQuestionService.java L162-L166 vs 同文件 L169-L177
- **问题**：简历题挂 → `directionFuture.cancel(true)` 掐掉在跑的 40% 方向题，重跑一次**全量 100%**——白扔一次已完成的调用；方向题挂 → 直接返回 60% 简历题，**不补量不校验**
- **影响**：同一件事（单路失败）两种待遇；一边浪费钱，一边面试题量缺 40%
- **修法**：方向题挂时复用已有的 40% 简历题，再补出剩余方向题（增量补跑）；或统一策略
- **话术**："降级分支设计不对称：一边 cancel 重跑全量、一边直接给残缺结果。正确做法是复用已有结果做增量补跑，既不浪费已完成的调用，也保证题量完整。"

## 3. 题量不足无校验 🟥

- **位置**：InterviewQuestionService.java L341-L350（`capToMainCount`）
- **问题**：LLM 少生成时只打一条 warn 就原样返回；没有任何"主问题数 < 请求数"的下限校验
- **影响**：要 8 题给 3 题，系统认为完全正常——**"非空但不够"是校验盲区**（空题单有兜底，不足量没有）
- **修法**：主问题数 < 请求量 → 用兜底题**补足**（不是全换，保留 AI 生成的优质部分）
- **话术**："我对'空'做了兜底，但对'不足'没有做下限校验。LLM 少生成是常态，应该用兜底题补齐到请求数，而不是让面试只有一半题。"

## 4. 简历题方法缺空题单检查 🟥

- **位置**：InterviewQuestionService.java L190-L224
- **问题**：`generateResumeQuestions` 拿到结果直接 `capToMainCount` 返回，没有像方向题那样的"主问题数=0 → 兜底"检查
- **影响**：简历题空题单只能靠外层 `generateQuestionsBySkill` 的两路皆空判断兜住；单看方法自身不设防
- **修法**：与方向题对齐，方法内补空题单检查

## 5. 日程解析：source 被当 providerId 传 🟥

- **位置**：InterviewParseService.java L115-L116
- **问题**：`parseWithAI(rawText, source)` 把平台来源（如 "feishu"）当 providerId 传给 `getChatClientOrDefault`
- **链路**："feishu" 非空非 blank → 不回退默认 → `loadProviderOrThrow("feishu")` → DB 查不到 → 抛 `IllegalArgumentException` → 外层 catch 吞掉 → `parser="none"`
- **影响**：**指定平台 + 正则失败**时 AI 兜底 100% 失败；表面像正常降级（前端提示手填），实际是错误被静默吞掉。危险在将来：注册表里真出现叫 "feishu" 的 provider 会悄悄走错模型
- **修法**：传真正的 providerId，`source` 只用于规则层选正则
- **话术**："`getChatClientOrDefault` 的默认回退只对 null/blank 生效，传非法 id 会抛异常；调用方又用 catch 吞掉，错误被两层吃掉。后来我把'平台来源'和'模型 provider'两个概念拆开了。"

---

## 6. 并发唯一约束冲突未优雅处理 + RustFS 孤儿文件 🟥

- **位置**：ResumePersistenceService.java L86-L89、ResumeUploadService.java L79-L88
- **问题**：并发上传同一文件时，SELECT 查重有竞态窗口双双通过，靠 contentHash 的 UNIQUE 约束在第二条 INSERT 时拦截——数据库能检测出来，但 `saveResume` 的 `catch (Exception e)` 太宽，把约束冲突和普通保存失败混在一起，统一抛"保存简历失败"
- **影响**：① 用户在竞态窗口看到误导性报错（本该返回已有记录），量级极低所以无感；② 更实际的坑：上传顺序是**先存 RustFS 再落库**，落库失败后 RustFS 里的文件没有清理逻辑 → 孤儿文件占存储
- **修法**：① 单独 catch `DataIntegrityViolationException`，按重复处理返回已有记录；② 落库失败时补偿删除已上传的 RustFS 文件（或改顺序：先落库再传文件，失败回滚删记录）
- **话术**："查重和唯一索引是双保险，但我核实后发现约束冲突被宽 catch 吞成通用报错，没有转成'返回已有记录'；而且先传文件后落库的顺序在失败时会留孤儿文件，正确做法是加补偿删除。"

---

## 7. 答案落库失败被静默吞掉，评估只读 DB 🟥

- **位置**：InterviewSessionService.java L341-L343、EvaluateStreamConsumer.java L113-L125
- **问题**：`submitAnswer` 的 DB 保存被 try/catch 包住只 `log.warn`（可用性优先的有意取舍）；但**评估消费者完全不读 Redis**——它从 DB 取 `session.getQuestionsJson()` + `findAnswersBySessionId()` 拼题
- **影响**：Redis 写成功、DB 失败时，缓存有效期内用户刷新无感（读 Redis 齐全），但 ① 该题在评估里 `userAnswer` 为空 → AI 按"未作答"评分；② Redis 24h 过期后从 DB 恢复 → 答案真丢
- **修法**：DB 写失败进补偿重试（本地消息表思路），或落库失败时给会话打"答案不完整"标记、评估前校验答案数
- **话术**："可用性优先是有意的取舍，但缺了补偿机制——评估只读 DB，落库失败会让那题按没答评分。正确做法是失败进补偿重试，而不是打条 warn 就过去。"

---

## 面试三句话（"有什么不足"时主动说）

1. 出题降级的兜底没在出口收口，`BusinessException` 会绕过兜底题导致建会话失败——重做时我会在方法出口统一落兜底
2. 两条降级分支不对称：一边白扔 40% 调用重跑全量、一边只给 60% 不补量——应该复用结果增量补跑
3. 题量只有"空"校验没有"不足"校验，LLM 少生成时该用兜底题补齐
4. 简历上传先传 RustFS 再落库，落库失败留孤儿文件且约束冲突被宽 catch 吞——加补偿删除和单独 catch
5. 答案落库失败只打 warn 没有补偿，而评估只读 DB——那题会被判"未作答"；失败应进补偿重试
