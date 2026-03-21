# AGENTS

本仓库采用“人类 + AI”协作模式推进。AI 不是项目记忆本身，`docs/` 才是长期事实来源。

## 阅读顺序

AI 开始任何任务前，默认按这个顺序读取最小上下文：

1. [`README.md`](/Users/apple/Project/linker-script/README.md)
2. [`docs/roadmap/current.md`](/Users/apple/Project/linker-script/docs/roadmap/current.md)
3. [`docs/product/prd.md`](/Users/apple/Project/linker-script/docs/product/prd.md)
4. [`docs/architecture/overview.md`](/Users/apple/Project/linker-script/docs/architecture/overview.md)
5. 与当前任务最相关的模块文档：
   - [`docs/modules/script-ingestion.md`](/Users/apple/Project/linker-script/docs/modules/script-ingestion.md)
   - [`docs/modules/analysis-and-review.md`](/Users/apple/Project/linker-script/docs/modules/analysis-and-review.md)
   - [`docs/modules/retrieval-and-generation.md`](/Users/apple/Project/linker-script/docs/modules/retrieval-and-generation.md)
6. [`docs/operations/development-workflow.md`](/Users/apple/Project/linker-script/docs/operations/development-workflow.md)

只有在这些文档不足以支持当前任务时，才继续查看代码。

## Source Of Truth

- 产品目标与边界：[`docs/product/prd.md`](/Users/apple/Project/linker-script/docs/product/prd.md)
- 当前阶段、优先级与非目标：[`docs/roadmap/current.md`](/Users/apple/Project/linker-script/docs/roadmap/current.md)
- 系统边界与模块关系：[`docs/architecture/overview.md`](/Users/apple/Project/linker-script/docs/architecture/overview.md)
- 项目结构与目录职责：[`docs/architecture/project-structure.md`](/Users/apple/Project/linker-script/docs/architecture/project-structure.md)
- 环境与部署策略：[`docs/architecture/environment-strategy.md`](/Users/apple/Project/linker-script/docs/architecture/environment-strategy.md)、[`docs/operations/deployment.md`](/Users/apple/Project/linker-script/docs/operations/deployment.md)
- 数据存储策略：[`docs/architecture/data-storage.md`](/Users/apple/Project/linker-script/docs/architecture/data-storage.md)
- 模块职责与接口边界：`docs/modules/*.md`
- 协作范式与新会话提示词：[`docs/ai-collaboration/framework.md`](/Users/apple/Project/linker-script/docs/ai-collaboration/framework.md)、[`docs/ai-collaboration/kickoff-prompt.md`](/Users/apple/Project/linker-script/docs/ai-collaboration/kickoff-prompt.md)

当前仓库不再保留旧 `doc/` 目录，项目事实统一维护在 `docs/`。

## 任务最小阅读集

- 接口、入库、状态流转相关任务：
  - `docs/product/prd.md`
  - `docs/modules/script-ingestion.md`
  - `docs/architecture/data-storage.md`
- 拆解、标签、点评、AI 调用相关任务：
  - `docs/modules/analysis-and-review.md`
  - `docs/architecture/overview.md`
  - `docs/architecture/data-storage.md`
- 检索、RAG、生成相关任务：
  - `docs/modules/retrieval-and-generation.md`
  - `docs/product/phase1-mvp.md`
  - `docs/architecture/data-storage.md`
- 环境、部署、联调相关任务：
  - `docs/architecture/environment-strategy.md`
  - `docs/operations/deployment.md`
  - `docs/operations/runbook.md`

## 文档更新规则

出现以下情况时，代码改动必须同步更新文档：

- 产品目标、范围、非目标改变：更新 `docs/product/prd.md` 和 `docs/roadmap/current.md`
- 新增或变更接口、异步流程、模块职责：更新对应 `docs/modules/*.md`
- 新增数据表、字段、索引、迁移策略：更新 `docs/architecture/data-storage.md`
- 新增环境变量、部署流程、运行命令：更新 `docs/architecture/environment-strategy.md` 或 `docs/operations/deployment.md`
- 开发验证流程变化：更新 `docs/operations/development-workflow.md`
- 已做出影响后续演进的重要选择：新增 ADR

## 何时新增 ADR

满足任一条件就应新增 `docs/architecture/decisions/` 下的 ADR：

- 这是一个难以回滚的技术选型
- 这个决策会影响多模块或多环境
- 当前方案明显排除了另一条可行路线
- 后续成员如果不知道“为什么这样做”，很容易误改

## 验证规则

- 纯文档改动：检查链接、路径、术语一致性
- Java 逻辑、接口或配置改动：执行 `./gradlew test`
- 涉及 `dev` 启动链路、环境变量或数据库联调：尽量补跑 [`scripts/smoke-dev.sh`](/Users/apple/Project/linker-script/scripts/smoke-dev.sh)
- 不能验证时，要明确写出未验证项和阻塞原因

## Git 规则

- 默认不自动 `push`
- 默认以小而清晰的提交边界组织改动
- 不回滚用户未明确要求回滚的改动
- 需要新分支时，默认使用 `codex/` 前缀
- 提交说明应同时覆盖代码、文档、验证三部分
