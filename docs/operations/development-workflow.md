# 开发工作流

## 目标

保证任何一次迭代都能在“代码 + 文档 + 验证”三件事上形成闭环。

## 默认流程

1. 从 [`AGENTS.md`](/Users/apple/Project/linker-script/AGENTS.md) 和最小阅读集进入任务
2. 明确本次改动影响的是产品、架构、模块还是运维
3. 实现改动
4. 执行最小验证
5. 更新受影响的 `docs/`
6. 输出结果、验证和风险

## 常用命令

### 运行测试

```bash
./gradlew test
```

### 默认启动

```bash
./gradlew bootRun
```

### `dev` 启动

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### `dev` smoke test

```bash
./scripts/smoke-dev.sh
```

## 改动后的文档同步规则

- 改接口：更新对应模块文档，必要时更新 README 示例
- 改表结构或迁移：更新 `docs/architecture/data-storage.md`
- 改环境变量或部署方式：更新环境策略与部署文档
- 改产品范围：更新 PRD 和路线图

## 提交前检查

- 改动是否和当前路线图一致
- 文档是否同步
- 是否做了最小验证
- 是否明确写清未验证项

## 适合新增 ADR 的情况

- 新引入基础设施
- 替换核心模型或检索策略
- 改变异步执行模型
- 改变数据库主存储或部署形态
