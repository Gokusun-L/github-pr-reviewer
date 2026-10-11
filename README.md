# GitHub PR AI Reviewer

基于 Webhook + Redis Stream 的 GitHub PR 自动审查机器人。

开发者在仓库提 PR 后，机器人自动拉取变更代码，调用 LLM 逐行分析，把发现的问题以**行内评论**写回 PR 页面。

## 当前状态

W1 开发中：同步版链路跑通阶段。

## 技术栈

Java 21 · Spring Boot 3.5 · Spring AI · Redis Stream · PostgreSQL · Docker

## 里程碑

- [ ] W1-D7 行内评论写回真实 PR
- [ ] W2 异步化 + 幂等 + 三级降级链
- [ ] W3 去重 + 增量审查 + 公网部署