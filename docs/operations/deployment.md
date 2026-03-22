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
- [`scripts/build-release-image.sh`](/Users/apple/Project/linker-script/scripts/build-release-image.sh)
- [`scripts/upgrade-prod.sh`](/Users/apple/Project/linker-script/scripts/upgrade-prod.sh)

## 构建

```bash
export IMAGE_TAG="$(date +%Y%m%d)-$(git rev-parse --short HEAD)-amd64"
export APP_VERSION="${IMAGE_TAG}"

./gradlew bootJar -PappVersion="${APP_VERSION}"
docker build \
  --platform linux/amd64 \
  --build-arg APP_VERSION="${APP_VERSION}" \
  --build-arg VCS_REF="$(git rev-parse HEAD)" \
  -t "linkscript:${IMAGE_TAG}" .
```

也可以直接使用：

```bash
./scripts/build-release-image.sh
```

## 运行前准备

- 准备可访问的 PostgreSQL
- 确认数据库已安装 `pgvector`
- 准备 AI Provider 的 API Key 和 Base URL
- 准备 Compose 所需的 `.env`

## 关键环境变量

- `IMAGE_REPOSITORY`
- `IMAGE_TAG`
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_MODEL`
- `OPENAI_EMBEDDING_MODEL`
- `LINKSCRIPT_VECTOR_DIMENSIONS`

## 当前部署特点

- Compose 文件默认通过 `IMAGE_REPOSITORY + IMAGE_TAG` 指定明确制品版本，不再依赖 `latest`
- Compose 文件默认关闭 `SPRING_FLYWAY_VALIDATE_ON_MIGRATE`
- 服务不直接暴露端口，依赖外部 `shared-proxy` 网络
- 生产容器默认使用 Java 21 JRE Alpine

## 发布建议

1. 先在可访问同一数据库的环境中执行 `./gradlew test`
2. 必要时用 `dev` profile 做一次 smoke 验证
3. 再构建镜像并更新 Compose
4. 发布后检查健康接口与核心日志

## Prod 自动升级脚本

仓库内提供了一条端到端生产升级脚本：

```bash
export PROD_HOST=117.72.207.52
export PROD_USER=root
export PROD_PASSWORD='your_password'
export PROD_DEPLOY_DIR=/root/apps/linker-script

./scripts/upgrade-prod.sh
```

脚本会自动完成：

- 可选执行 `./gradlew test`
- 构建版本化镜像和 `tar.gz` 制品
- 上传制品与最新 `compose.yaml`
- 备份远端 `compose.yaml` 和 `.env`
- 更新远端 `.env` 里的 `IMAGE_REPOSITORY` / `IMAGE_TAG`
- `docker load` 新镜像并执行 `docker compose up -d`
- 容器内健康检查
- 出错时自动回滚到备份配置

常用变量：

- `IMAGE_TAG`
- `APP_VERSION`
- `RUN_TESTS=false`
- `SKIP_BUILD=true`
- `HEALTH_TIMEOUT_SECONDS=180`
