# 环境策略

## 环境划分

### `test`

- 用途：自动化测试
- 数据库：H2 内存库
- Flyway：关闭
- AI：关闭，走 fallback
- 入口配置：[`src/test/resources/application-test.yml`](/Users/apple/Project/linker-script/src/test/resources/application-test.yml)

### `default`

- 用途：本地默认运行或自定义环境
- 数据库：本地 PostgreSQL，来自 `DB_URL/DB_USERNAME/DB_PASSWORD`
- Flyway：开启
- AI：默认开启，走 OpenAI 兼容接口
- 入口配置：[`src/main/resources/application.yml`](/Users/apple/Project/linker-script/src/main/resources/application.yml)

### `dev`

- 用途：联调和外部数据库验证
- 数据库：远程 PostgreSQL，来自 `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`
- Flyway：开启
- AI：默认开启，可接 SiliconFlow
- 当前默认模型：`Pro/moonshotai/Kimi-K2.5` + `Qwen/Qwen3-Embedding-8B`
- 入口配置：[`src/main/resources/application-dev.yml`](/Users/apple/Project/linker-script/src/main/resources/application-dev.yml)

### Compose 部署环境

- 用途：服务器容器化运行
- 入口文件：[`deploy/linker-script/compose.yaml`](/Users/apple/Project/linker-script/deploy/linker-script/compose.yaml)
- 依赖：外部 PostgreSQL、外部反向代理网络 `shared-proxy`

## 关键环境变量

### 数据库

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD`

### AI

- `LINKSCRIPT_AI_ENABLED`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_CHAT_MODEL`
- `OPENAI_EMBEDDING_MODEL`
- `OPENAI_TEMPERATURE`

### 向量与日志

- `LINKSCRIPT_VECTOR_DIMENSIONS`
- `LINKSCRIPT_LEXICAL_CANDIDATE_LIMIT`
- `LINKSCRIPT_LOGGING_ENABLED`
- `LINKSCRIPT_LOGGING_MAX_PAYLOAD_LENGTH`

## 默认策略

- 非测试环境默认通过 Flyway 管理结构
- AI 配置缺失时，服务仍可启动，但会退化到 fallback
- `dev` 优先作为“真实环境行为”的近似验证环境

## 当前注意事项

- `dev` 启动必须提供数据库密码，否则 PostgreSQL 认证会失败
- 历史数据库如果缺失 Flyway 文件，需保证本仓库 `db/migration` 与已执行版本兼容
- 向量维度当前默认 4096，应与 embedding 模型保持一致
