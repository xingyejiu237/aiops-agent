# Phase 1 总结 — AIOps 智能运维助手

---

## 一、Phase 1 大致实现

### 1.1 功能概览

Phase 1 实现了一个基于 ReAct 推理模式的单 Agent 智能运维助手，核心能力：**能对话 → Agent 自主调工具 → 查 Prometheus 指标 + 查知识库 → SSE 流式返回**。

| # | 功能 | API | 说明 |
|---|------|-----|------|
| 1 | 文档上传 | `POST /api/documents` | 上传文档到 Qdrant 向量库，支持 Markdown，自动按 `\n\n` 分片，title 拼入每个分片提高检索命中率 |
| 2 | 批量上传 | `POST /api/documents/batch` | 批量上传多篇文档 |
| 3 | 文档检索 | `GET /api/documents?keyword=&topK=` | 通过向量相似度检索知识库 |
| 4 | 删除文档 | `DELETE /api/documents/{id}` | 删除指定文档分片 |
| 5 | 清空文档 | `DELETE /api/documents` | 清空整个知识库 |
| 6 | SSE 流式对话 | `GET /api/chat/stream` | Agent 流式回复，ReAct 自动推理 + Tool 调用 |
| 7 | 非流式对话 | `POST /api/chat` | 非流式对话（调试用） |
| 8 | 清空会话 | `DELETE /api/sessions/{id}` | 清空会话历史 |
| 9 | 可视化前端 | `GET /` | 单页面前端，左侧对话 + 右侧知识库管理 |

### 1.2 技术架构

```
┌──────────────────────────────────────────────────┐
│              前端 (index.html + SSE)              │
│     左侧对话区(Markdown渲染) │ 右侧知识库管理      │
├──────────────────────────────────────────────────┤
│          Spring Boot 3.3 + Spring AI 1.0          │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │       OpsAgent (ReAct 单 Agent)              │ │
│  │  SystemPrompt: SRE运维工程师角色定义           │ │
│  │  Tools:                                       │ │
│  │    ├─ PrometheusTools  查询CPU/内存/QPS/延迟   │ │
│  │    └─ KnowledgeTools   知识库向量检索          │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │   RAG    │ │ Session  │ │Document  │          │
│  │Component │ │Component │ │Component │          │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘          │
├───────┼─────────────┼────────────┼────────────────┤
│  Qdrant向量库   InMemory会话   Qdrant文档存储      │
├──────────────────────────────────────────────────┤
│  Docker: Qdrant(6333) │ Prometheus(9090) │ MySQL(3306) │
│  DeepSeek API │ Ollama(bge-m3, 11434)               │
└──────────────────────────────────────────────────┘
```

### 1.3 项目文件清单

```
aiops-agent/
├── docker-compose.yml                    # Qdrant + Prometheus + MySQL
├── infra/
│   └── prometheus.yml                    # 采集 aiops-agent actuator 指标
├── pom.xml
├── src/main/java/com/aiops/
│   ├── AiopsApplication.java             # 启动类
│   ├── agent/
│   │   └── OpsAgent.java                 # ReAct Agent 核心（ChatClient + @Tool）
│   ├── tool/
│   │   ├── PrometheusTools.java          # @Tool 注解，查 Prometheus 指标
│   │   └── KnowledgeTools.java           # @Tool 注解，查知识库
│   ├── service/
│   │   └── PrometheusService.java        # Prometheus HTTP 客户端 + 响应格式化
│   ├── component/
│   │   ├── rag/
│   │   │   ├── RagComponent.java         # RAG 接口
│   │   │   └── RagComponentImpl.java     # RAG 实现（VectorStore + ChatClient）
│   │   ├── document/
│   │   │   ├── DocumentComponent.java    # 文档管理接口
│   │   │   └── DocumentComponentImpl.java# 文档管理实现（分片+存储+删除+清空）
│   │   └── session/
│   │       ├── SessionManager.java       # 会话管理接口
│   │       ├── Conversation.java         # 会话实体
│   │       └── InMemorySessionManager.java# 内存会话管理
│   ├── controller/
│   │   ├── ChatController.java           # 对话接口（SSE + 非流式）
│   │   ├── DocumentController.java       # 文档管理接口
│   │   └── SessionController.java        # 会话管理接口
│   └── config/
│       ├── AiConfig.java                 # ChatClient Bean
│       └── WebClientConfig.java          # WebClient 配置
├── src/main/resources/
│   ├── application.yml                   # 主配置
│   └── static/
│       └── index.html                    # 前端单页面
```

### 1.4 核心设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| Agent 架构 | 单 Agent + @Tool | Spring AI 的 @Tool 注解天然支持 ReAct，LLM 自动决定调哪个工具，无需手动实现 Think/Act/Observe 循环 |
| Tool 注册方式 | `@Tool` 注解（非 Function 接口） | 更简洁，Spring AI 1.0 推荐方式，直接在方法上加注解 |
| 向量数据库 | Qdrant | 专业向量数据库，支持 HNSW 索引 + payload 过滤，比 Redis Stack 更适合 RAG 场景 |
| Embedding 模型 | Ollama + bge-m3 | 本地部署，无需 API 调用费用，1024 维中文效果好 |
| 会话管理 | InMemory | Phase 1 够用，Phase 3 改 Redis/MySQL |
| 流式输出 | SSE (Flux) | Spring WebFlux 的 Flux 天然支持 SSE，前端 EventSource 接收 |

---

## 二、Phase 1 踩的坑

### 坑 0（启动阶段）：Spring AI 依赖 + 配置连环坑

这 5 个坑是项目首次启动时遇到的最关键问题，不解决根本跑不起来：

#### 0-1：spring-ai-ollama 不会自动注册 EmbeddingModel

**现象**：启动报错，找不到 `EmbeddingModel` Bean，Qdrant VectorStore 无法初始化。

**根因**：`spring-ai-ollama` 只是一个普通依赖，不包含自动配置。需要用 `spring-ai-starter-model-ollama` 才有 Spring Boot AutoConfiguration 自动注册 `OllamaEmbeddingModel` Bean。

**修复**：pom.xml 中将依赖改为：
```xml
<!-- 错误 -->
<artifactId>spring-ai-ollama</artifactId>
<!-- 正确 -->
<artifactId>spring-ai-starter-model-ollama</artifactId>
```

**教训**：Spring AI 的 starter 和非 starter 依赖区别很大，starter 才有自动配置，非 starter 需要手动声明 Bean。

#### 0-2：双 ChatModel 冲突（DeepSeek + Ollama）

**现象**：启动报错 `expected single matching bean but found 2: deepseekChatModel, ollamaChatModel`。

**根因**：`spring-ai-starter-model-ollama` 自动注册了 `OllamaChatModel`，而 `spring-ai-starter-model-deepseek` 注册了 `DeepSeekChatModel`，两个都实现了 `ChatModel` 接口，Spring 不知道注入哪个。

**修复**：在 DeepSeek ChatModel 上加 `@Primary`，让 Spring 默认注入 DeepSeek：
```java
@Bean
@Primary
public ChatModel primaryChatModel(DeepSeekChatModel deepSeekChatModel) {
    return deepSeekChatModel;
}
```

**教训**：引入多个 Spring AI Model Starter 时，必须用 `@Primary` 指定默认 ChatModel，否则 Bean 冲突。

#### 0-3：Qdrant Collection 不存在导致启动失败

**现象**：启动报错 `Collection 'aiops_knowledge' not found`，VectorStore 初始化时 Qdrant 里还没有这个 Collection。

**根因**：Spring AI 的 QdrantVectorStore 默认不会自动创建 Collection，需要手动建或配置自动建。

**修复**：application.yml 中加 `initialize-schema: true`：
```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        initialize-schema: true  # 自动创建 Collection
```

**教训**：Spring AI 的 VectorStore 需要显式开启 schema 初始化，不像 JPA 的 `ddl-auto: update` 那样默认行为。

#### 0-4：QdrantClient gRPC 端口是 6334 不是 6333

**现象**：`DocumentComponentImpl` 中直接使用 Qdrant Java Client 操作（delete/clear/count）时连接失败。

**根因**：Qdrant 有两个端口——6333 是 HTTP REST API 端口，6334 是 gRPC 端口。Spring AI 的 `QdrantVectorStore` 内部用 HTTP（6333），但 `QdrantClient` Java SDK 用 gRPC（6334）。如果配成 6333，gRPC 连接会失败。

**修复**：`QdrantClient` 初始化时端口用 6334：
```java
QdrantClient client = new QdrantClient(
    QdrantGrpcClient.newBuilder("localhost", 6334, false).build()
);
```

**教训**：Qdrant 的 6333 和 6334 是两个不同协议，HTTP vs gRPC，不能混用。

#### 0-5：Ollama `dimensions` 属性不存在

**现象**：启动报错，`spring.ai.ollama.embedding.options.dimensions` 配置项无法识别。

**根因**：Spring AI 1.0 的 Ollama Embedding 配置中没有 `dimensions` 属性，bge-m3 的维度由模型自身决定（1024 维），不需要也不支持在配置中指定。

**修复**：从 application.yml 中去掉无效的 `dimensions` 配置：
```yaml
# 删除这行，无效配置
# spring.ai.ollama.embedding.options.dimension: 1024
```

向量维度在 `spring.ai.vectorstore.qdrant.embedding-dimension: 1024` 中指定，这是给 Qdrant Collection 建表用的，不是给 Ollama 的。

**教训**：Spring AI 的配置项要看实际版本的 AutoConfiguration 源码，文档可能滞后。不要想当然地加配置。

### 坑 1：Prometheus Tool 的 PromQL 指标名对不上

**现象**：Agent 调 `prometheusQuery` 总返回 400 或空数据。

**根因**：`buildPromQL()` 里的指标名是按通用 Prometheus + JMX Exporter 写的（如 `process_cpu_seconds_total`），但 Prometheus 实际采集的是 Spring Boot Actuator (Micrometer) 暴露的指标，名字完全不同：

| 原来用的（JMX 风格） | 实际应该是（Micrometer 风格） |
|---|---|
| `process_cpu_seconds_total` | `process_cpu_usage` |
| `process_resident_memory_bytes` | `jvm_memory_used_bytes` / `jvm_memory_max_bytes` |
| `http_server_requests_seconds_count` | `http_server_requests_seconds_count`（这个倒是一样） |

**修复**：把 `buildPromQL()` 改成 Spring Boot Actuator 的实际指标名。

**教训**：写 Tool 前先去 Prometheus UI 确认实际有哪些指标，别凭经验猜。

### 坑 2：LLM 不知道该传什么 job 名给 Prometheus

**现象**：Agent 调 Tool 时 LLM 随便编了一个服务名（如 `default`），Prometheus 找不到 `{job="default"}`，返回 400。

**根因**：`@Tool` 的 `description` 只写了"服务名称，如 order-service"，LLM 不知道当前环境实际有哪些服务，就瞎编。

**修复**：在 Tool description 里明确写 `当前可用的服务名(job)为: aiops-agent`，LLM 就会传正确的名字。

**教训**：Tool 的 description 要包含**当前环境的实际可用值**，不能只给示例。LLM 不会自动发现环境里有什么。

### 坑 3：文档上传时 title 没存入向量

**现象**：搜索"JVM调优手册"搜不到内容，因为 title 字段只存了元数据，没进向量。

**根因**：`DocumentComponentImpl.loadFromString()` 只把 `content` 分片存入向量，`title` 没拼进去。

**修复**：新增 `loadFromString(content, delimiter, title)` 重载，把 title 拼到每个分片前面再 embedding。

**教训**：RAG 系统中，文档标题是重要的语义信号，应该一起存入向量。

### 坑 4：Spring AI @Tool 与 Function 接口的 API 差异

**现象**：最初按 `项目计划Phase1.md` 设计的 `Function<Request, Response>` 接口写 Tool，结果 Spring AI 1.0 的 `ChatClient.tools()` 方法期望的是带 `@Tool` 注解的对象，不是 Function。

**根因**：Spring AI 1.0 正式版改了 Tool 注册机制，从 Function 接口改成了 `@Tool` 注解方式。

**修复**：把 Tool 类从 `implements Function<Req, Resp>` 改成普通 `@Component` + `@Tool` 注解方法。

**教训**：Spring AI 还在快速迭代，文档可能滞后，以实际 API 为准。

### 坑 5：SSE 接口是 GET 不是 POST

**现象**：前端或 curl 用 POST 调 `/api/chat/stream` 返回 405。

**根因**：`ChatController` 的 SSE 端点用的是 `@GetMapping`（因为 SSE 规范是 GET + 长连接），但直觉上对话接口应该是 POST。

**修复**：前端用 `fetch(url)` GET 请求，或浏览器直接打开 URL。

**教训**：SSE 规范用 GET，如果需要传大 body 应该先 POST 创建会话再 GET 订阅流。Phase 1 简化处理，参数走 query string。

### 坑 6：PowerShell 中文乱码

**现象**：用 `Invoke-RestMethod` 测试对话 API，返回的中文全是乱码。

**根因**：PowerShell 默认编码不是 UTF-8，`Invoke-RestMethod` 解码时用了系统默认编码。

**修复**：这不是代码 bug。用浏览器、IDEA HTTP Client 或 `curl` 测试就不乱码。

**教训**：Windows 终端编码问题是老大难，测试时用浏览器最省心。

---

## 三、Phase 1 如何启动项目

### 3.1 前置条件

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | `java -version` 验证 |
| Maven | 3.8+ | `mvn -v` 验证 |
| Docker Desktop | 最新 | 运行 Qdrant / Prometheus / MySQL |
| Ollama | 最新 | 运行 bge-m3 Embedding 模型 |

### 3.2 启动步骤

```powershell
# === Step 1: 启动 Docker 基础设施 ===
cd e:\IDEA_code\AI_projcet2\aiops_agent
docker compose up -d

# 验证（3个容器都应 Up）
docker ps

# === Step 2: 启动 Ollama 并拉取 Embedding 模型 ===
# 如果 Ollama 没装，先装：https://ollama.com/download
ollama pull bge-m3

# 验证 Ollama 运行中
ollama list

# === Step 3: 配置 API Key ===
# 编辑 src/main/resources/application.yml
# 修改 spring.ai.deepseek.api-key 为你的 DeepSeek API Key
# 当前已配置：sk-8c51dfea06a142da98ae879bccf7900a

# === Step 4: 构建 & 启动 Spring Boot ===
mvn clean package -DskipTests
java -jar target/aiops-agent-1.0.0.jar

# 或者用 Maven 直接运行：
# mvn spring-boot:run

# === Step 5: 验证启动成功 ===
# 浏览器打开 http://localhost:8080
# 或 curl http://localhost:8080/actuator/health
# 应返回 {"status":"UP"}
```

### 3.3 Docker 服务端口

| 服务 | 端口 | 用途 |
|------|------|------|
| Qdrant | 6333 (gRPC) / 6334 (HTTP) | 向量数据库 |
| Prometheus | 9090 | 指标存储与查询 |
| MySQL | 3306 | 数据库（Phase 1 未深度使用） |
| Ollama | 11434 | 本地 Embedding 模型 |
| Spring Boot | 8080 | 应用服务 |

### 3.4 常见启动问题

| 问题 | 排查 |
|------|------|
| `Connection refused: localhost:6333` | Qdrant 没启动，`docker compose up -d` |
| `Connection refused: localhost:11434` | Ollama 没启动，`ollama serve` |
| `model 'bge-m3' not found` | 没拉模型，`ollama pull bge-m3` |
| DeepSeek API 401 | API Key 过期或错误，去 https://platform.deepseek.com 重新获取 |
| 端口 8080 被占 | `netstat -ano | findstr :8080` 查占用进程，或改 `application.yml` 的 `server.port` |

---

## 四、Phase 1 测试用例及测试方法

> 浏览器打开 `http://localhost:8080`，所有功能都可以在前端页面可视化测试，无需命令行。

### 4.1 前端页面布局说明

```
┌────────────────────────────────┬──────────────────┐
│         左侧：对话区            │   右侧：知识库管理  │
│                                │                  │
│  ① 顶部：Session ID 输入框     │  ④ 标题 + 内容输入 │
│  ② 中间：对话消息流            │  ⑤ 上传/示例按钮   │
│  ③ 底部：输入框 + 发送/清空     │  ⑥ 文档列表/搜索   │
│                                │  ⑦ 状态栏         │
└────────────────────────────────┴──────────────────┘
```

### 4.2 前端测试用例

#### 用例 1：上传示例文档到知识库

**操作**：点击右侧 **"上传示例文档"** 按钮

**预期**：
- 弹窗提示"批量上传成功！共 XX 个分片存入知识库"
- 自动刷新文档列表，显示 4 篇文档：JVM Full GC、MySQL 慢查询、Redis 内存溢出、CPU 飙高

---

#### 用例 2：手动上传单篇文档

**操作**：
1. 右侧标题栏输入 `Nginx 502排查手册`
2. 内容栏输入：
```
## Nginx 502 Bad Gateway 排查

### 现象
Nginx 返回 502，上游服务不可达

### 处理步骤
1. 检查上游服务是否存活：curl upstream_ip:port
2. 查看 Nginx error.log：tail -f /var/log/nginx/error.log
3. 检查 upstream 超时配置：proxy_read_timeout
4. 检查后端服务连接数是否打满
```
3. 点击 **"上传文档"**

**预期**：弹窗提示"上传成功！分为 X 个分片"，文档出现在列表中

---

#### 用例 3：检索知识库

**操作**：点击右侧 **"搜索文档"** 按钮，输入 `Redis`

**预期**：
- 文档列表刷新，显示包含 Redis 关键词的文档
- 每条文档显示相似度百分比（如 68.3%）
- 列表顶部显示总数

---

#### 用例 4：Agent 对话（自动调知识库 Tool）

**操作**：在左侧输入框输入 `服务频繁Full GC怎么排查？`，按回车或点"发送"

**预期**：
- 状态栏显示"Agent 思考中..."
- Agent 回复逐字流式输出（Markdown 渲染，含标题/表格/代码块）
- 回答内容引用了知识库中的 JVM 排障手册
- 状态栏恢复"就绪"

**验证点**：回答中是否提到了 `jstat`、`jmap` 等知识库中的排障命令

---

#### 用例 5：Agent 对话（自动调 Prometheus Tool）

**操作**：在左侧输入框输入 `查一下aiops-agent的CPU和内存指标`

**预期**：
- Agent 自动调用 `prometheusQuery` 工具
- 回答中包含真实的 CPU 使用率、JVM 堆内存使用率等数值
- 数值来自 aiops-agent 自身的 Actuator 指标（Prometheus 采集的）

**验证点**：回答中是否有具体的百分比数值（如"CPU 使用率: 0.05"、"堆内存使用: 72.3%"）

---

#### 用例 6：Agent 组合调工具（Prometheus + 知识库）

**操作**：在左侧输入框输入 `aiops-agent服务响应变慢了，帮我排查一下。不管指标是否异常，都可以查一下知识库看看有没有相关排查方案。`

**预期**：
- Agent 先调 Prometheus 查指标
- 再调知识库搜排障方案
- 综合两个工具的结果给出回答

**验证点**：回答是否同时引用了指标数据 + 知识库方案

---

#### 用例 7：多轮对话（上下文记忆）

**操作**：
1. 第一轮输入：`服务频繁Full GC怎么排查？`
2. 等 Agent 回复完毕
3. 第二轮输入：`那如果我还发现内存使用率一直很高呢？`

**预期**：
- 第二轮 Agent 记住上一轮的 "Full GC" 上下文
- 回答将 Full GC 和内存高关联分析，而非从零开始

**验证点**：第二轮回答中是否自然承接了 Full GC 的话题

---

#### 用例 8：清空会话

**操作**：点击左下角 **"清空会话"** 按钮

**预期**：
- 对话消息清空
- 显示"会话已清空"提示
- 之后再输入问题，Agent 不记得之前的上下文

---

#### 用例 9：更换 Session ID

**操作**：修改顶部 Session ID 输入框为 `session-002`，再输入问题

**预期**：
- 新 Session 是全新对话，Agent 不记得之前 `session-001` 的上下文
- 切回 `session-001` 后，之前的对话历史仍在

**验证点**：不同 Session 的上下文隔离

---

#### 用例 10：删除文档

**操作**：
1. 在文档列表中，点击某条文档右上角的 **"✕"** 按钮
2. 确认删除

**预期**：
- 文档从列表中消失
- 再次搜索该文档关键词，不再返回该文档

---

## 五、Phase 1 完成总结

### 5.1 实现的功能

✅ 项目骨架搭建完成（Spring Boot 3.3 + Spring AI 1.0）
✅ NeuxAI 组件迁移完成（RAG、Document、Session）
✅ ReAct Agent 实现完成（OpsAgent + @Tool）
✅ Prometheus 指标查询完成（PrometheusTools）
✅ 知识库检索完成（KnowledgeTools + RagComponent）
✅ SSE 流式对话完成（ChatController）
✅ 文档管理完成（DocumentController）
✅ 前端页面完成（index.html）
✅ docker-compose 基础设施完成

### 5.2 技术亮点

1. **Spring AI @Tool 注解**：比 Function 接口更简洁，LLM 自动决策调用
2. **Qdrant 向量库**：专业向量数据库，比 Redis Stack 更适合 RAG
3. **SSE 流式输出**：前端实时展示 Agent 推理过程
4. **单页面前端**：无需构建工具，直接静态 HTML + CDN

### 5.3 下一步（Phase 2）

- [ ] Loki 日志查询（LokiQueryTool）
- [ ] 服务拓扑查询（TopologyComponent）
- [ ] 告警 Webhook 接收（WebhookController）
- [ ] 排障报告生成（IncidentReport）
