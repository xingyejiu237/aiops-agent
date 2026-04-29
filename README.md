# AIOps Agent - 智能运维助手

基于 ReAct 推理模式的单 Agent 智能运维助手，解决研发运维团队 OnCall 排障效率问题。

## 项目定位

Agent 像一个 SRE 工程师一样，通过"观察→思考→行动"循环，自主完成故障定位与建议生成。

## Phase 1 功能

- **ReAct 推理对话** - 用户提问后，Agent 自主多轮 Think→Act→Observe，最终给出结论
- **Prometheus 指标查询** - 通过 HTTP API 查询服务 CPU/内存/QPS/延迟等指标
- **RAG 知识库检索** - 从运维手册/SOP 文档中检索相关排障方案
- **文档管理** - 上传、删除、列表查看知识库文档（向量化存储）
- **多轮上下文记忆** - 同一会话内保持对话上下文，支持追问
- **SSE 流式输出** - 前端实时展示 Agent 推理过程和最终回答

## 技术架构

- Spring Boot 3.3 + Spring AI 1.0
- DeepSeek API (大模型)
- Qdrant (向量数据库)
- Ollama + bge-m3 (Embedding)
- Prometheus (指标监控)

## 快速开始

```bash
# 1. 启动 Docker 基础设施
docker compose up -d

# 2. 启动 Ollama 并拉取 Embedding 模型
ollama pull bge-m3

# 3. 构建并运行
mvn clean package -DskipTests
java -jar target/aiops-agent-1.0.0.jar

# 4. 打开浏览器访问
http://localhost:8080
```

## 项目文档

- [项目计划](./docs/项目计划.md)
- [Phase 1 详细设计](./docs/项目计划Phase1.md)
- [Phase 1 总结](./docs/总结Phase1.md)

## 版权声明

版权所有 © 2025 xingyejiu237. 保留所有权利。

未经作者明确书面许可，任何人不得使用、复制、修改或分发本软件。
