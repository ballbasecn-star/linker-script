# 架构总览

## 系统目标

LinkScript 采用“采集-拆解-存储-检索-生成”的闭环结构，把内容资产变成 AI 可直接利用的结构化知识。

## 主要组件

- API 层：提供导入、查询、检索、生成、标签接口
- ScriptService：负责素材入库、状态管理、详情聚合
- AnalysisListener：在事务提交后异步触发分析
- AnalysisService：负责拆解、embedding、点评、自动打标
- VectorSearchService：负责主题检索与相似片段召回
- GenerationService：负责样本选择、Prompt 组装、生成结果落库
- AiGateway：负责 OpenAI 兼容接口调用
- PostgreSQL：存储脚本、碎片、标签、生成日志和向量

## 主链路

1. 客户端调用导入接口
2. 脚本主表写入或幂等更新
3. 事务提交后发布异步分析任务
4. 分析服务调用 LLM 做拆解、打标与点评
5. 为碎片生成 embedding 并落库
6. 用户按主题检索碎片或选择样本生成新文案

## 设计原则

- 单体优先：当前以单体 Spring Boot 保持迭代效率
- 异步解耦：分析不阻塞导入请求
- AI 可退化：AI 不可用时仍能走 fallback 分析、embedding 与生成
- 文档外置：项目事实不依赖聊天上下文
- 数据优先：脚本、碎片、标签、日志都应可追踪

## 当前重要实现约束

- 分析事件使用 `@TransactionalEventListener(AFTER_COMMIT)`，避免读到未提交数据
- 数据迁移统一通过 Flyway 管理
- `dev` 环境默认连接外部 PostgreSQL
- AI Provider 使用 OpenAI 兼容接口，当前默认偏向 SiliconFlow

## 主要风险

- 向量维度和索引能力之间仍有性能折中
- 当前无权限与租户隔离，默认是单团队内部工具形态
- 生成质量高度依赖样本质量和提示词约束
