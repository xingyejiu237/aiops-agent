# AIOps Agent

基于 Spring AI + DeepSeek 的智能运维助手，通过 ReAct 推理自主调用工具排查故障。

## 技术栈

Spring Boot 3.3 / Spring AI 1.0 / DeepSeek / Qdrant / Prometheus / Ollama (bge-m3)

## 快速启动

```bash
# 1. 启动 Docker 基础设施（Qdrant + Prometheus + MySQL）
docker compose up -d

# 2. 启动 Ollama Embedding 模型
ollama pull bge-m3

# 3. 配置 API Key
# 编辑 src/main/resources/application.yml 填写 DeepSeek API Key

# 4. 启动项目
mvn spring-boot:run

# 5. 打开浏览器
# http://localhost:8080
```

## 功能

- ReAct 推理对话，Agent 自主调用工具
- Prometheus 指标查询（CPU/内存/QPS/延迟/错误率/GC）
- RAG 知识库检索（Qdrant + bge-m3）
- 文档上传/检索/删除管理
- SSE 流式输出推理过程
- 多轮会话上下文

## 项目结构

```
src/main/java/com/aiops/
├── agent/          OpsAgent（ReAct 推理核心）
├── tool/           Prometheus + 知识库检索工具
├── component/      RAG / 文档 / 会话组件
├── controller/     HTTP 接口（对话 / 文档 / 会话）
├── service/        Prometheus HTTP 客户端
└── config/         Spring AI + WebClient 配置
```
