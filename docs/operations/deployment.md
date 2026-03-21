# 部署说明

## 当前部署形态

当前仓库采用：

- Gradle 构建 Spring Boot 可执行 Jar
- Docker 多阶段构建镜像
- Docker Compose 运行应用容器
- 外部 PostgreSQL 提供数据存储

## 关键文件

- [`Dockerfile`](/Users/apple/Project/linker-script/Dockerfile)
- [`deploy/linker-script/compose.yaml`](/Users/apple/Project/linker-script/deploy/linker-script/compose.yaml)
- [`deploy/linker-script/.env.example`](/Users/apple/Project/linker-script/deploy/linker-script/.env.example)

## 构建

```bash
./gradlew bootJar
docker build -t linkscript:latest-amd64 .
```

## 运行前准备

- 准备可访问的 PostgreSQL
- 确认数据库已安装 `pgvector`
- 准备 AI Provider 的 API Key 和 Base URL
- 准备 Compose 所需的 `.env`

## 关键环境变量

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`
- `OPENAI_EMBEDDING_MODEL`
- `LINKSCRIPT_VECTOR_DIMENSIONS`

## 当前部署特点

- Compose 文件默认关闭 `SPRING_FLYWAY_VALIDATE_ON_MIGRATE`
- 服务不直接暴露端口，依赖外部 `shared-proxy` 网络
- 生产容器默认使用 Java 21 JRE Alpine

## 发布建议

1. 先在可访问同一数据库的环境中执行 `./gradlew test`
2. 必要时用 `dev` profile 做一次 smoke 验证
3. 再构建镜像并更新 Compose
4. 发布后检查健康接口与核心日志
