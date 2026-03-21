# LinkScript AI 协作框架

## 目的

这份文档把通用的 AI 协作范式，收敛成适用于 LinkScript 的项目操作方式。

它回答 4 个问题：

- 哪些文档是长期记忆
- AI 应该先读什么
- 人和 AI 如何分工
- 代码、文档、验证如何保持一条闭环

## 当前项目假设

- 项目类型：AI 驱动的内容分析与创作 Web 应用
- 当前阶段：已有 MVP，正在进入“可长期迭代”的工程化阶段
- 主要协作对象：开发者、运营、内容团队，以及后续加入的新 AI 会话
- 当前部署形态：Spring Boot 单体服务 + PostgreSQL，使用 Docker 镜像和 Compose 部署应用

## 文档分层

### 面向人的入口

- [`README.md`](/Users/apple/Project/linker-script/README.md)

### 面向 AI 的协作入口

- [`AGENTS.md`](/Users/apple/Project/linker-script/AGENTS.md)

### 产品事实

- [`docs/product/prd.md`](/Users/apple/Project/linker-script/docs/product/prd.md)
- [`docs/product/phase1-mvp.md`](/Users/apple/Project/linker-script/docs/product/phase1-mvp.md)

### 架构事实

- [`docs/architecture/overview.md`](/Users/apple/Project/linker-script/docs/architecture/overview.md)
- [`docs/architecture/project-structure.md`](/Users/apple/Project/linker-script/docs/architecture/project-structure.md)
- [`docs/architecture/environment-strategy.md`](/Users/apple/Project/linker-script/docs/architecture/environment-strategy.md)
- [`docs/architecture/data-storage.md`](/Users/apple/Project/linker-script/docs/architecture/data-storage.md)
- [`docs/architecture/decisions/`](/Users/apple/Project/linker-script/docs/architecture/decisions)

### 模块事实

- [`docs/modules/script-ingestion.md`](/Users/apple/Project/linker-script/docs/modules/script-ingestion.md)
- [`docs/modules/analysis-and-review.md`](/Users/apple/Project/linker-script/docs/modules/analysis-and-review.md)
- [`docs/modules/retrieval-and-generation.md`](/Users/apple/Project/linker-script/docs/modules/retrieval-and-generation.md)

### 运行事实

- [`docs/operations/development-workflow.md`](/Users/apple/Project/linker-script/docs/operations/development-workflow.md)
- [`docs/operations/deployment.md`](/Users/apple/Project/linker-script/docs/operations/deployment.md)
- [`docs/operations/runbook.md`](/Users/apple/Project/linker-script/docs/operations/runbook.md)

## Source Of Truth 约定

- 产品方向、用户、边界：`docs/product/`
- 当前要做什么：`docs/roadmap/current.md`
- 系统结构与环境：`docs/architecture/`
- 模块行为与接口边界：`docs/modules/`
- 运维与开发方式：`docs/operations/`

说明：

- 新的事实应优先写入 `docs/`

## 人与 AI 分工

### 人负责

- 确认产品方向和优先级
- 对关键取舍拍板
- 提供业务反馈和真实使用体验
- 对生产发布负责

### AI 负责

- 读取最小必要上下文
- 实现代码和文档改动
- 解释设计取舍
- 做可行的最小验证
- 把决策回写到 `docs/`

## 默认工作流

1. 从 `AGENTS.md` 和最小阅读集进入任务
2. 明确当前变更属于产品、架构、模块、运维中的哪一类
3. 实现代码或文档改动
4. 执行最小验证
5. 同步更新相关文档
6. 输出本次结论、验证结果和剩余风险

## 文档维护原则

- 不把所有知识塞进 README
- 不用聊天记录作为长期事实来源
- 一份文档尽量只回答一类问题
- 新事实出现后，应落在最接近职责边界的那份文档里

## 仍需逐步补充的个性化信息

这些信息已经会影响后续文档细化，但当前可以先带着假设推进：

- LinkScript 的最终产品形态更偏内部内容中台，还是对外 SaaS
- 未来是否会进入多团队、多租户、权限体系
- 生产环境是否需要严格的审批、回滚、灰度流程
