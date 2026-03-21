# 数据存储设计

## 总体原则

- 主存储为 PostgreSQL
- 结构迁移由 Flyway 统一管理
- 逻辑关联优先使用业务键 `script_uuid`
- 兼顾结构化字段、JSONB 字段和向量字段

## 主要表

### `ls_script`

用途：

- 存储脚本主记录
- 记录状态、统计数据、热度分、点评结果

关键字段：

- `script_uuid`
- `title`
- `content`
- `transcript`
- `source_platform`
- `external_id`
- `stats_json`
- `status`
- `heat_score`
- `heat_level`
- `review_json`
- `created_at`

关键约束：

- `source_platform + external_id` 唯一约束，用于幂等导入

### `ls_logic_fragment`

用途：

- 存储脚本拆解后的逻辑片段

关键字段：

- `script_uuid`
- `f_type`
- `content`
- `logic_desc`
- `embedding`
- `confidence`

说明：

- 当前向量字段已按 4096 维使用，需与 embedding 模型维度保持一致
- 现有 PostgreSQL/pgvector 环境下，ANN 索引能力仍需要结合实际版本验证

### `ls_tag`

用途：

- 存储标签定义

### `ls_script_tag`

用途：

- 存储脚本与标签的关联

### `ls_generation_log`

用途：

- 记录生成请求、参考样本、生成内容和后续人工编辑结果

## 迁移策略

- 所有结构变更进入 `src/main/resources/db/migration/`
- 已发布版本的迁移不应随意改写
- 补偿式修复优先通过新增迁移完成
- Repeatable migration 需要与历史数据库状态保持兼容

## 测试环境策略

- `test` 环境使用 H2，并通过 `schema-dev.sql` 初始化最小结构
- 这意味着 PostgreSQL 特有行为不会被完整覆盖
- 影响启动链路或 PG 语义的改动，应尽量在 `dev` 再验证一次

## 当前存储层风险

- 高维 embedding 的索引能力受 pgvector 版本和运行环境限制
- `review_json` 当前以内联 JSONB 存储，适合当前阶段；如果后续引入人工复评历史，再考虑拆表
