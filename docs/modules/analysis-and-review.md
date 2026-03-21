# 模块：分析、打标与点评

## 职责

这个模块负责把原始脚本拆成逻辑片段，并进一步补充标签、点评和向量表示。

## 主流程

1. 导入事务提交后触发 `AnalysisRequestedEvent`
2. 异步监听器将状态更新为 `ANALYZING`
3. 调用 AI 拆解片段
4. AI 失败时走规则拆解
5. 为片段生成 embedding
6. 生成精选候选点评
7. 自动打标签
8. 完成后更新脚本状态

## 主要实现

- [`AnalysisListener.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/analysis/AnalysisListener.java)
- [`AnalysisService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/analysis/AnalysisService.java)
- [`FallbackScriptAnalyzer.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/analysis/FallbackScriptAnalyzer.java)
- [`ContentReviewService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/analysis/ContentReviewService.java)
- [`FallbackContentReviewer.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/analysis/FallbackContentReviewer.java)
- [`EmbeddingService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/vector/EmbeddingService.java)
- [`TagService.java`](/Users/apple/Project/linker-script/src/main/java/com/linkscript/core/tag/TagService.java)

## 当前分析输出

- 逻辑片段：`HOOK / SETUP / VALUE / BODY / CTA / TWIST`
- 片段逻辑说明
- 片段置信度
- 行业、情绪、受众标签
- 精选候选点评
- 风险提示

## 精选候选点评原则

点评是内部判断，不代表平台官方最终结论。

当前重点评估：

- 内容完整度
- 获得感
- 惊喜感
- 真实性
- 专业度
- 可信度
- 有趣度

## 关键约束

- 分析任务必须在事务提交后触发
- AI 不可用时，系统仍需产出可落库的 fallback 结果
- 点评结果当前存储在 `ls_script.review_json`

## 当前风险

- AI 输出格式可能漂移，需持续依赖严格 JSON 解析与 fallback
- 自动标签和点评的准确性仍依赖提示词和素材质量
