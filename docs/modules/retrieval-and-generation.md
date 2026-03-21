# 模块：检索与生成实验室

## 职责

这个模块负责把已沉淀的素材片段重新利用起来，用于检索、选样和生成新脚本。

## 入口

- `GET /api/v1/fragments/search`
- `POST /api/v1/compositions/generate`

## 检索逻辑

- 优先使用 embedding 进行相似度检索
- 如果向量检索受限，保留词法候选和 fallback 路径
- 可按片段类型过滤，例如只搜索 `HOOK`

## 生成逻辑

- 用户可显式传入样本 UUID
- 如果未传样本，系统可自动从库中召回候选
- 生成结果会记录到 `ls_generation_log`
- AI 失败时走模板生成 fallback

## 主要实现

- [`VectorSearchService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/vector/VectorSearchService.java)
- [`GenerationService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/generation/GenerationService.java)
- [`LabController.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/api/LabController.java)

## 当前质量关注点

- embedding 质量直接影响召回样本
- 高维向量的数据库索引能力仍需进一步优化
- 生成质量不仅取决于模型，也取决于参考样本质量与 Prompt 约束
