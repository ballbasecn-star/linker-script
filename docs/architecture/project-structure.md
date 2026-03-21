# 项目结构

## 顶层目录

```text
linker-script/
├── README.md
├── AGENTS.md
├── docs/
├── deploy/
├── scripts/
├── src/
├── build.gradle
├── settings.gradle
├── Dockerfile
└── gradlew
```

## 目录职责

### `src/main/java/com/linkscript`

- `api/`：REST 接口层
- `core/`：核心业务逻辑
- `domain/`：实体、DTO、Repository
- `infra/`：AI、日志、异步、异常、Web 配置

### `src/main/resources`

- `application*.yml`：环境配置
- `db/migration/`：Flyway 迁移
- `static/`：静态前端资源
- `schema-dev.sql`：H2/Test 初始化表结构

### `src/test`

- 单元测试与集成测试
- 当前重点覆盖：热度评分、生成、检索、AI 网关、回退逻辑、接口契约

### `docs`

长期维护中的协作文档。后续 AI 与人协作默认以这里为入口。

### `deploy/linker-script`

部署用 Compose 配置与环境变量示例。

### `scripts`

项目级辅助脚本。当前主要是 `smoke-dev.sh` 用于 `dev` 联调验证。

## 包级说明

### `api`

- `IngestController`：素材导入
- `ScriptController`：脚本列表、详情、碎片搜索、碎片修正
- `TagController`：标签管理
- `LabController`：样本驱动生成

### `core`

- `script`：脚本入库与详情聚合
- `analysis`：异步拆解、自动打标、点评
- `vector`：embedding 与检索
- `generation`：生成逻辑
- `score`：热度评分
- `tag`：标签系统

### `infra`

- `ai`：AI Provider 封装与配置
- `async`：异步线程执行器
- `logging`：接口日志、任务日志、请求追踪
- `exception`：统一异常响应
- `web`：CORS 等 Web 配置
