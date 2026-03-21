# LinkScript

LinkScript 是一个面向短视频创作团队的脚本分析与再生产平台，用于把素材沉淀成可拆解、可检索、可生成的结构化内容资产。

当前仓库已经切换到“人类 + AI”长期协作方式：

- 协作入口：[`AGENTS.md`](/Users/apple/Project/linker-script/AGENTS.md)
- 产品事实：[`docs/product/prd.md`](/Users/apple/Project/linker-script/docs/product/prd.md)
- 当前阶段：[`docs/roadmap/current.md`](/Users/apple/Project/linker-script/docs/roadmap/current.md)
- 架构入口：[`docs/architecture/overview.md`](/Users/apple/Project/linker-script/docs/architecture/overview.md)
- 开发流程：[`docs/operations/development-workflow.md`](/Users/apple/Project/linker-script/docs/operations/development-workflow.md)

说明：

- `docs/` 是当前维护中的协作文档体系，也是后续 AI 协作的主要事实来源。
- 早期 `doc/` 目录中的 PRD/TDD 内容已经收敛进 `docs/`，后续不再维护旧目录。

## 项目现状

项目起步于早期 PRD/TDD 设计稿，当前已整理为 `docs/` 下的长期协作文档，并在此基础上补了接口契约、日志、点评、测试与部署文档。

## 已实现能力 (包括 v1.1 质量基建)

- 素材摄取：`POST /api/v1/scripts/ingest` (自动计算爆款评分 Heat Score)
- 异步分析：AI 拆解逻辑碎片并返回置信度 (Confidence)，同时进行 AI 自动打标 (行业/情绪/受众)
- 碎片存储：写入 `ls_script`、`ls_logic_fragment`、`ls_generation_log`、`ls_tag`、`ls_script_tag`
- 碎片校正：`PUT /api/v1/fragments/{id}` (手动修正 AI 拆解错的碎片)
- 语义检索：`GET /api/v1/fragments/search`
- 素材筛选：`GET /api/v1/scripts` (支持按标签 `tags` 和热度 `heatLevel` 分页筛选)
- 文案生成：`POST /api/v1/compositions/generate`
- 详情查询：`GET /api/v1/scripts/{scriptUuid}` (返回关联标签及热度分数)

## 技术说明

- Spring Boot 3.4
- Java 21
- PostgreSQL 15 + `pgvector`
- Flyway 初始化表结构
- AI 调用支持 OpenAI 兼容接口；未配置 API Key 时自动走本地兜底逻辑

## 启动前准备

1. 安装 JDK 21+
2. 安装 Gradle 8.7+
3. 创建 PostgreSQL 数据库 `linkscript`
4. 安装扩展：`CREATE EXTENSION IF NOT EXISTS vector;`

## 环境变量

```bash
export DB_URL=jdbc:postgresql://localhost:5432/linkscript
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

export OPENAI_API_KEY=your_key
export OPENAI_BASE_URL=https://api.openai.com/v1
export OPENAI_CHAT_MODEL=gpt-4.1-mini
export OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

如果不配置 `OPENAI_API_KEY`，系统仍可运行，但会使用启发式拆解、哈希向量和模板生成兜底。

## 本地运行

```bash
gradle bootRun
```

## Dev Profile 本地启动

```bash
gradle bootRun --args='--spring.profiles.active=dev'
```

`dev` profile 默认连接外部 PostgreSQL：

```bash
export PGHOST=117.72.207.52
export PGPORT=5432
export PGDATABASE=postgres
export PGUSER=postgres
export PGPASSWORD=your_password
```

如果你希望 `dev` 环境执行真实的大模型拆解/生成，再补充：

```bash
export LINKSCRIPT_AI_ENABLED=true
export OPENAI_API_KEY=your_key
export OPENAI_BASE_URL=https://api.siliconflow.cn/v1
export OPENAI_CHAT_MODEL='Pro/zai-org/GLM-4.7'
export OPENAI_EMBEDDING_MODEL='Qwen/Qwen3-Embedding-8B'
```

`dev` profile 默认已经对齐 SiliconFlow 兼容接口：
- chat: `Pro/moonshotai/Kimi-K2.5`
- embedding: `Qwen/Qwen3-Embedding-8B`

如果 embedding 或 chat 接口不可用，系统仍会自动退化到本地 embedding fallback、规则拆解和模板生成模式。

## Dev Smoke Test

```bash
export PGHOST=********
export PGPORT=5432
export PGDATABASE=postgres
export PGUSER=postgres
export PGPASSWORD='your_password'

./scripts/smoke-dev.sh
```

脚本会使用 `dev` profile 在本地拉起服务，随后执行健康检查、素材导入、异步分析、碎片搜索、指定样本生成和自动选样生成，最后自动停止服务。

## 运行测试

```bash
gradle test
```

## 核心接口示例

### 1. 导入素材

```bash
curl -X POST http://localhost:8080/api/v1/scripts/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "如何用AI写代码",
    "content": "别再只会让AI帮你润色了。真正能提效的人，都先把任务拆成钩子、问题、动作和结果。想要我把这个提示词模版发你，评论区留一个AI。",
    "sourcePlatform": "douyin",
    "externalId": "123456",
    "statistics": {
      "likes": 100,
      "shares": 50
    }
  }'
```

### 2. 素材列表筛选 (v1.1 新增)

```bash
curl 'http://localhost:8080/api/v1/scripts?tags=职场,焦虑&heatLevel=S,A&page=0&size=20'
```

### 3. 校正碎片 (v1.1 新增)

```bash
curl -X PUT http://localhost:8080/api/v1/fragments/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "HOOK",
    "content": "修改后的钩子文案",
    "logicDesc": "修正描述"
  }'
```

### 4. 搜索 Hook

```bash
curl 'http://localhost:8080/api/v1/fragments/search?topic=%E8%81%8C%E5%9C%BA%E7%84%A6%E8%99%91&type=HOOK&limit=3'
```

### 5. 生成文案

```bash
curl -X POST http://localhost:8080/api/v1/compositions/generate \
  -H 'Content-Type: application/json' \
  -d '{
    "topic": "新产品发布",
    "sampleUuids": ["uuid1", "uuid2"],
    "options": {
      "tone": "幽默",
      "length": 300
    }
  }'
```
