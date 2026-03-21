# Phase 1 MVP 范围

## 目标

Phase 1 的目标不是做完整内容平台，而是先证明以下主链路能稳定工作：

素材导入 -> 异步拆解 -> 标签与点评 -> 碎片检索 -> 样本生成

## In Scope

- `POST /api/v1/scripts/ingest`
- `GET /api/v1/scripts`
- `GET /api/v1/scripts/{scriptUuid}`
- `GET /api/v1/fragments/search`
- `PUT /api/v1/fragments/{id}`
- `POST /api/v1/compositions/generate`
- `GET/POST/DELETE /api/v1/tags` 相关标签接口
- 精选候选点评与风险提示
- `dev` 联调 smoke test

## Out Of Scope

- 鉴权与角色权限
- 自动抓取外部平台数据
- 人工审核工作流
- 复杂发布流程
- 完整的可观测性平台接入

## MVP 验收信号

- 同一条素材可被幂等导入，不产生脏重复数据
- 异步分析在事务提交后触发
- 分析结果可落库并通过详情接口返回
- 检索与生成可在无人工干预下跑通
- `./gradlew test` 可通过
- `scripts/smoke-dev.sh` 可跑通 `dev` 关键链路

## 当前已知限制

- 当前是单体服务，扩展方式主要靠模块化而非服务拆分
- 4096 维向量在现有 PostgreSQL/pgvector 环境下未必能建立理想的 ANN 索引
- 前端仍是静态资源工作台，不是完整前后端分离应用
