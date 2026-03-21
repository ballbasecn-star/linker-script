# 模块：素材导入与脚本管理

## 职责

这个模块负责把外部素材变成系统内脚本资产，并维持主记录状态。

## 入口

- `POST /api/v1/scripts/ingest`
- `GET /api/v1/scripts`
- `GET /api/v1/scripts/{scriptUuid}`

## 核心行为

- 根据 `sourcePlatform + externalId` 做幂等导入
- 写入脚本主表
- 计算 `heatScore` 和 `heatLevel`
- 将状态置为 `PENDING`
- 发布分析事件
- 聚合标签、碎片、点评信息返回详情

## 主要实现

- [`ScriptService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/script/ScriptService.java)
- [`IngestController.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/api/IngestController.java)
- [`ScriptController.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/api/ScriptController.java)
- [`HeatScoreCalculator.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/score/HeatScoreCalculator.java)

## 输入

- 标题
- 正文或 transcript
- 来源平台
- 外部 ID
- 统计信息

## 输出

- 导入响应
- 列表页摘要
- 详情页聚合数据

## 关键约束

- 导入请求应尽快返回，不等待分析完成
- 脚本详情应能反映异步状态变化
- 任何幂等更新都要清空旧点评并重新进入分析流程
