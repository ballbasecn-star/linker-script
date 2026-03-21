# 运行排障手册

## 启动失败：PostgreSQL 认证报错

典型现象：

- `SCRAM-based authentication, but no password was provided`

处理方式：

- 检查 `PGPASSWORD` 或 `DB_PASSWORD` 是否已提供
- 确认当前 profile 读取的是哪套环境变量

## 启动失败：Flyway 校验报缺失迁移

典型现象：

- 历史数据库记录了本地仓库不存在的 migration

处理方式：

- 检查 `src/main/resources/db/migration/` 是否与数据库历史兼容
- 不要随意删除已上线数据库用过的 migration 文件
- 优先通过补偿迁移修复，而不是重写已执行版本

## 启动失败：向量列迁移异常

处理方式：

- 检查当前数据库里的 `vector` 扩展和列类型
- 检查迁移是否把 `vector` 字段误当成 `text` 处理
- 优先新增修复迁移，不直接改写已执行版本

## 分析结果一直不完成

处理方式：

- 查看 `async.analysis.*` 日志
- 查看 `ai.chat.*` 和 `ai.embedding.*` 日志
- 检查 AI Key、Base URL、模型名是否有效
- 检查数据库可写性和状态字段是否被更新

## `dev` 联调建议顺序

1. 启动服务
2. 先看 `/actuator/health`
3. 再跑 [`scripts/smoke-dev.sh`](/Users/apple/Project/linker-script/scripts/smoke-dev.sh)
4. 失败时优先看应用日志中的 `requestId`、`taskId`
