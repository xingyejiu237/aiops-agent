# Phase 1 详细设计文档 — 最小可用

> 目标：用户能对话 → Agent 用 ReAct 自主调工具 → 查 Prometheus 指标 + 查知识库 → SSE 流式返回

---

## 一、Phase 1 范围确认

### 做什么

| 编号 | 功能 | 说明 |
|------|------|------|
| P1-1 | 项目骨架 | Spring Boot 3.3 + Spring AI 1.0 + Qdrant + Maven |
| P1-2 | OpsAgent | ReAct 单 Agent，注册 2 个 Tool |
| P1-3 | PrometheusQueryTool | 查询 Prometheus HTTP API |
| P1-4 | KnowledgeSearchTool | 调用 RagComponent 检索知识库 |
| P1-5 | RagComponent 迁移 | 从 NeuxAI 迁移，VectorStore 换 Qdrant |
| P1-6 | DocumentComponent 迁移 | 从 NeuxAI 迁移，去 Redis 依赖 |
| P1-7 | SessionManager 迁移 | 从 NeuxAI 迁移，InMemory 实现 |
| P1-8 | ChatController | SSE 流式对话接口 |
| P1-9 | DocumentController | 文档上传/删除/列表接口 |
| P1-10 | docker-compose | Qdrant + Prometheus + Ollama + MySQL |

### 不做什么（Phase 2+）

- LokiQueryTool、TopologyQueryTool、MysqlQueryTool
- WebhookController / AlertService
- 前端页面（Phase 1 用 curl / Swagger 验证）
- IncidentReport 持久化

---

## 二、接口设计

### 2.1 对话接口

#### SSE 流式对话

```
GET /api/chat/stream?sessionId={sessionId}&message={message}
Content-Type: text/event-stream
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID，用于多轮上下文 |
| message | String | 是 | 用户消息（URL编码） |

**响应**：SSE 事件流

```
event: thinking
data: {"type":"thinking","content":"我需要先查一下订单服务的CPU指标..."}

event: tool_call
data: {"type":"tool_call","tool":"prometheusQuery","args":{"service":"order-service","metric":"cpu_usage","duration":"1h"}}

event: tool_result
data: {"type":"tool_result","tool":"prometheusQuery","content":"CPU使用率: 45.2%"}

event: thinking
data: {"type":"thinking","content":"CPU正常，再查一下内存..."}

event: tool_call
data: {"type":"tool_call","tool":"knowledgeSearch","args":{"query":"JVM内存调优"}}

event: tool_result
data: {"type":"tool_result","tool":"knowledgeSearch","content":"【运维手册】JVM内存调优方案：1.增大堆内存..."}

event: answer
data: {"type":"answer","content":"根据分析，订单服务CPU正常，但建议关注内存使用情况。运维手册中提到..."}

event: done
data: [DONE]
```

**说明**：
- `thinking`：Agent 的推理过程（LLM 中间输出）
- `tool_call`：Agent 调用工具的请求
- `tool_result`：工具返回的结果
- `answer`：最终回答
- `done`：流结束标记

> **Phase 1 简化方案**：Spring AI 的 `stream().chatResponse()` 返回的 Flux 中包含 Tool 调用信息，
> 但 SSE 事件类型的具体分类取决于 Spring AI 1.0 的流式返回格式。
> Phase 1 先用最简格式：每条 SSE data 直接是文本片段，前端拼接展示。
> Phase 2 再细化事件分类（thinking/tool_call/tool_result/answer）。

#### Phase 1 实际 SSE 格式

```
GET /api/chat/stream?sessionId=abc123&message=订单服务响应慢怎么办
Content-Type: text/event-stream

data: 我来帮你排查

data: 订单服务响应变慢的问题。让我先查一下

data: 它的CPU和内存指标。

data: 【调用工具: prometheusQuery】

data: 根据Prometheus数据，CPU使用率45%，正常。

data: 让我再查一下知识库中有没有相关排障方案。

data: 【调用工具: knowledgeSearch】

data: 知识库中找到了JVM调优方案。

data: 根据分析，建议：1.关注内存使用 2.参考知识库中的JVM调优SOP。

data: [DONE]
```

#### 非流式对话（备用）

```
POST /api/chat
Content-Type: application/json

请求体：
{
  "sessionId": "abc123",
  "message": "订单服务响应慢怎么办"
}

响应：
{
  "sessionId": "abc123",
  "answer": "根据分析，建议：1.关注内存使用 2.参考知识库中的JVM调优SOP。",
  "toolCalls": [
    {"tool": "prometheusQuery", "args": {"service":"order-service","metric":"cpu_usage","duration":"1h"}, "result": "CPU使用率: 45.2%"},
    {"tool": "knowledgeSearch", "args": {"query":"JVM内存调优"}, "result": "【运维手册】JVM内存调优方案..."}
  ]
}
```

### 2.2 文档管理接口

#### 上传文档

```
POST /api/documents
Content-Type: application/json

请求体：
{
  "title": "JVM调优手册",
  "content": "## JVM Full GC 排查方案\n\n### 现象\n服务频繁Full GC...\n\n### 处理步骤\n1. jstat -gcutil查看各区使用率\n2. jmap -heap查看堆内存配置\n3. 增大堆内存或优化对象创建"
}

响应：
{
  "success": true,
  "chunkCount": 3,
  "message": "文档已分片并存入向量库"
}
```

#### 批量上传文档

```
POST /api/documents/batch
Content-Type: application/json

请求体：
{
  "documents": [
    {"title": "JVM调优手册", "content": "..."},
    {"title": "MySQL慢查询排查", "content": "..."},
    {"title": "Redis高可用方案", "content": "..."}
  ]
}

响应：
{
  "success": true,
  "totalChunks": 12,
  "details": [
    {"title": "JVM调优手册", "chunkCount": 3},
    {"title": "MySQL慢查询排查", "chunkCount": 5},
    {"title": "Redis高可用方案", "chunkCount": 4}
  ]
}
```

#### 查询文档列表

```
GET /api/documents?keyword={keyword}&topK={topK}

请求参数：
  keyword: 搜索关键词（可选，不传则返回全部）
  topK: 返回数量（可选，默认10）

响应：
{
  "total": 3,
  "documents": [
    {"id": "doc-001", "text": "JVM Full GC 排查方案...", "score": 0.92},
    {"id": "doc-002", "text": "MySQL慢查询排查步骤...", "score": 0.85},
    {"id": "doc-003", "text": "Redis高可用部署方案...", "score": 0.78}
  ]
}
```

#### 删除文档

```
DELETE /api/documents/{docId}

响应：
{
  "success": true,
  "message": "文档已删除"
}
```

#### 清空所有文档

```
DELETE /api/documents

响应：
{
  "success": true,
  "message": "所有文档已清空"
}
```

### 2.3 会话管理接口

#### 清空会话

```
DELETE /api/sessions/{sessionId}

响应：
{
  "success": true,
  "message": "会话已清空"
}
```

---

## 三、组件封装详细设计

### 3.1 OpsAgent

```java
package com.aiops.agent;

@Component
public class OpsAgent {

    private final ChatClient chatClient;
    private final SessionManager sessionManager;
    private final List<Function<?, ?>> tools;

    /**
     * 流式对话
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return SSE 文本流
     */
    public Flux<String> chatStream(String message, String sessionId) {
        // 1. 保存用户消息到会话
        sessionManager.addUserMessage(sessionId, message);

        // 2. 获取历史消息（最近20条）
        List<Message> history = sessionManager.getRecentMessages(sessionId, 20);

        // 3. 调用 LLM，注册 Tools，流式返回
        Flux<String> responseFlux = chatClient.prompt()
                .system(OPS_SYSTEM_PROMPT)
                .messages(history)
                .user(message)
                .tools(tools.toArray(new Function[0]))
                .stream()
                .content();  // Spring AI 返回文本片段流

        // 4. 收集完整回答，保存到会话
        //    （用 cache + subscribe 实现：流式返回前端的同时，收集完整回答存入 session）

        return responseFlux;
    }

    /**
     * 非流式对话
     */
    public String chat(String message, String sessionId) {
        sessionManager.addUserMessage(sessionId, message);
        List<Message> history = sessionManager.getRecentMessages(sessionId, 20);

        String answer = chatClient.prompt()
                .system(OPS_SYSTEM_PROMPT)
                .messages(history)
                .user(message)
                .tools(tools.toArray(new Function[0]))
                .call()
                .content();

        sessionManager.addAssistantMessage(sessionId, answer);
        return answer;
    }
}
```

**System Prompt**（存放在 `resources/prompts/ops-agent-system.st`）：

```
你是一位经验丰富的SRE运维工程师，负责排查线上服务故障。

## 你的工作方式
- 使用 ReAct 模式工作：先思考(Think)，再行动(Act)，观察结果(Observe)，循环推理
- 每次只做一步推理，根据上一步结果决定下一步
- 不要猜测，用工具查到的数据说话

## 可用工具
1. prometheusQuery - 查询服务的 CPU、内存、QPS、延迟等运行指标
2. knowledgeSearch - 从运维知识库中检索排障方案和SOP

## 排障流程
1. 先用 prometheusQuery 查看异常服务的核心指标（CPU、内存、QPS、延迟）
2. 如果指标异常，用 knowledgeSearch 查找对应的排障方案
3. 如果指标正常，告知用户当前服务指标正常，建议排查其他方向

## 回答规范
- 给出明确的原因分析，不要含糊其辞
- 引用工具查到的具体数据作为依据
- 给出可操作的建议，不要泛泛而谈
- 如果无法定位问题，诚实说明并建议人工排查

## 语言
- 使用中文回答
```

### 3.2 PrometheusQueryTool

```java
package com.aiops.tool;

@Component
public class PrometheusQueryTool
        implements Function<PrometheusQueryTool.Request, PrometheusQueryTool.Response> {

    private final PrometheusService prometheusService;

    // Spring AI 通过 @Description 注解读取工具描述，LLM 据此决定是否调用
    @Description("查询 Prometheus 监控指标，获取服务的 CPU 使用率、内存使用率、QPS、响应延迟等运行数据")
    public record Request(
        @Description("服务名称，如 order-service, user-service, gateway") String service,
        @Description("指标类型，可选值: cpu_usage, memory_usage, qps, latency, error_rate, gc_count") String metric,
        @Description("查询时间范围，如 5m, 30m, 1h, 6h, 24h，默认1h") String duration
    ) {}

    public record Response(String result) {}

    @Override
    public Response apply(Request request) {
        String duration = request.duration() != null ? request.duration() : "1h";
        String promql = buildPromQL(request.service(), request.metric(), duration);
        String result = prometheusService.queryRange(promql, duration);
        return new Response(result);
    }

    /**
     * 根据指标类型构建 PromQL
     */
    private String buildPromQL(String service, String metric, String duration) {
        return switch (metric) {
            case "cpu_usage"    -> "100 - (avg by(instance) (rate(process_cpu_seconds_total{job=\"%s\"}[5m])) * 100)".formatted(service);
            case "memory_usage" -> "avg by(instance) (process_resident_memory_bytes{job=\"%s\"}) / 1024 / 1024".formatted(service);
            case "qps"          -> "sum by(instance) (rate(http_server_requests_seconds_count{job=\"%s\"}[5m]))".formatted(service);
            case "latency"      -> "histogram_quantile(0.95, sum by(le) (rate(http_server_requests_seconds_bucket{job=\"%s\"}[5m])))".formatted(service);
            case "error_rate"   -> "sum by(instance) (rate(http_server_requests_seconds_count{job=\"%s\",status=~\"5..\"}[5m])) / sum by(instance) (rate(http_server_requests_seconds_count{job=\"%s\"}[5m]))".formatted(service, service);
            default             -> "up{job=\"%s\"}".formatted(service);  // 默认查服务是否在线
        };
    }
}
```

### 3.3 KnowledgeSearchTool

```java
package com.aiops.tool;

@Component
public class KnowledgeSearchTool
        implements Function<KnowledgeSearchTool.Request, KnowledgeSearchTool.Response> {

    private final RagComponent ragComponent;

    @Description("从运维知识库中检索排障方案、SOP文档、运维手册等，输入关键词或问题描述")
    public record Request(
        @Description("检索关键词或问题描述，如 'JVM Full GC调优'、'MySQL慢查询排查'") String query,
        @Description("返回结果数量，默认5") Integer topK
    ) {}

    public record Response(String result) {}

    @Override
    public Response apply(Request request) {
        int topK = request.topK() != null ? request.topK() : 5;
        List<String> docs = ragComponent.retrieve(request.query(), topK);

        if (docs.isEmpty()) {
            return new Response("知识库中未找到与 \"" + request.query() + "\" 相关的内容");
        }

        String result = String.join("\n---\n", docs);
        return new Response(result);
    }
}
```

**关键点**：KnowledgeSearchTool 只做检索不做生成，把检索结果返回给 LLM，由 LLM 在 ReAct 循环中自己组织最终回答。这样比让 RagComponent.query() 直接生成更灵活——LLM 可以结合 Prometheus 数据 + 知识库内容综合回答。

### 3.4 PrometheusService

```java
package com.aiops.service;

@Service
public class PrometheusService {

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    private final WebClient webClient;

    /**
     * 执行 PromQL 范围查询
     * @param promql   PromQL 表达式
     * @param duration 时间范围
     * @return 格式化后的查询结果
     */
    public String queryRange(String promql, String duration) {
        long end = System.currentTimeMillis() / 1000;
        long start = end - parseDuration(duration);

        // GET /api/v1/query_range?query=...&start=...&end=...&step=60
        String url = String.format("%s/api/v1/query_range?query=%s&start=%d&end=%d&step=60",
                prometheusUrl, URLEncoder.encode(promql, StandardCharsets.UTF_8), start, end);

        String json = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();

        return formatPrometheusResponse(json);
    }

    /**
     * 解析 Prometheus JSON 响应，提取关键数值
     * 不返回原始 JSON，而是返回人类可读的摘要
     */
    private String formatPrometheusResponse(String json) {
        // 解析 JSON，提取 metric 标签 + 数值
        // 输出格式：
        //   指标: process_cpu_seconds_total{instance="192.168.1.10:8080"}
        //   当前值: 45.2%, 5分钟前: 43.1%, 30分钟前: 42.8%
        //   趋势: 缓慢上升
        // ...
    }

    private long parseDuration(String duration) {
        // "5m" → 300, "1h" → 3600, "24h" → 86400
        if (duration.endsWith("m")) return Long.parseLong(duration.replace("m","")) * 60;
        if (duration.endsWith("h")) return Long.parseLong(duration.replace("h","")) * 3600;
        return 3600; // 默认1小时
    }
}
```

### 3.5 RagComponent（迁移自 NeuxAI）

**改动点**：
1. 包名 `com.example.spring_ai_agent.component.rag` → `com.aiops.component.rag`
2. `RagComponentImpl` 中 `VectorStore` 由 Spring AI 自动注入，依赖从 Redis Stack → Qdrant，**代码不用改**（Spring AI 抽象层屏蔽实现）
3. 新增 `@Value` 配置项：`rag.top-k`、`rag.similarity-threshold`

```java
package com.aiops.component.rag;

@Component
public class RagComponentImpl implements RagComponent {

    private final VectorStore vectorStore;  // Spring AI 自动注入 QdrantVectorStore
    private final ChatClient chatClient;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.5}")
    private double defaultSimilarityThreshold;

    // ... 其余逻辑与 NeuxAI 相同，retrieve() 和 query() 不变
}
```

### 3.6 DocumentComponent（迁移自 NeuxAI，去 Redis 依赖）

**改动点**：
1. 包名改
2. **去掉 `StringRedisTemplate` 依赖**，文档元数据通过 Qdrant 的 payload 管理
3. `listDocuments()` 改用 Qdrant scroll API 或保留 vectorStore.similaritySearch
4. `deleteDocument()` 改用 `vectorStore.delete(List.of(docId))`
5. `clearAll()` 和 `count()` 需要 Qdrant 客户端直接操作（Spring AI VectorStore 没有清空/计数 API）

```java
package com.aiops.component.document;

@Component
public class DocumentComponentImpl implements DocumentComponent {

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;  // 直接操作 Qdrant 用于 delete/clear/count

    @Override
    public void store(List<Document> documents) {
        vectorStore.add(documents);
    }

    @Override
    public void deleteDocument(String docId) {
        vectorStore.delete(List.of(docId));
    }

    @Override
    public void clearAll() {
        // 通过 Qdrant Client 删除 collection 再重建
        // 或者用 qdrantClient.deleteCollection() + 重新创建
    }

    @Override
    public long count() {
        // qdrantClient.getCollectionInfo() 获取 points count
    }

    // loadFromString(), loadAndStore() 与 NeuxAI 相同，无需改动
}
```

### 3.7 SessionManager（直接迁移，无改动）

包名改即可，InMemorySessionManager 在 Phase 1 够用。

---

## 四、配置设计

### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: aiops-agent

  # DeepSeek 配置
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
          temperature: 0.3    # 运维排障需要精确，温度低

    # Qdrant 向量库配置
    vectorstore:
      qdrant:
        host: localhost
        port: 6333
        collection-name: aiops_knowledge
        embedding-dimension: 1024   # bge-m3 输出维度

    # Ollama Embedding 配置
    ollama:
      base-url: http://localhost:11434
      embedding:
        model: bge-m3
        options:
          dimension: 1024

  # MySQL 配置
  datasource:
    url: jdbc:mysql://localhost:3306/aiops?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

# Prometheus 配置
prometheus:
  url: http://localhost:9090

# RAG 参数
rag:
  top-k: 5
  similarity-threshold: 0.5
  chunk-size: 500

# 会话参数
session:
  max-history: 20       # 每次传给 LLM 的最大历史消息数

logging:
  level:
    com.aiops: DEBUG
    org.springframework.ai: DEBUG
```

---

## 五、Spring AI 配置类

```java
package com.aiops.config;

@Configuration
public class AiConfig {

    /**
     * ChatClient Bean
     * Spring AI 自动配置 DeepSeekChatModel，这里只需构建 ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

**说明**：
- `spring-ai-starter-model-deepseek` 自动注册 `DeepSeekChatModel` Bean
- `spring-ai-starter-vector-store-qdrant` 自动注册 `QdrantVectorStore` Bean
- `spring-ai-ollama` 自动注册 `OllamaEmbeddingModel` Bean
- 我们只需构建 `ChatClient`，其余 Spring AI 全自动

---

## 六、Controller 层设计

### 6.1 ChatController

```java
package com.aiops.controller;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpsAgent opsAgent;

    /**
     * SSE 流式对话
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String sessionId,
            @RequestParam String message) {
        return opsAgent.chatStream(message, sessionId)
                .concatWith(Flux.just("[DONE]"));
    }

    /**
     * 非流式对话（备用/调试用）
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = opsAgent.chat(request.message(), request.sessionId());
        return Map.of("sessionId", request.sessionId(), "answer", answer);
    }

    public record ChatRequest(String sessionId, String message) {}
}
```

### 6.2 DocumentController

```java
package com.aiops.controller;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentComponent documentComponent;

    /**
     * 上传文档
     */
    @PostMapping
    public Map<String, Object> upload(@RequestBody DocumentUploadRequest request) {
        int chunkCount = documentComponent.loadAndStore(request.content());
        return Map.of("success", true, "chunkCount", chunkCount);
    }

    /**
     * 批量上传
     */
    @PostMapping("/batch")
    public Map<String, Object> batchUpload(@RequestBody BatchUploadRequest request) {
        int totalChunks = 0;
        for (var doc : request.documents()) {
            totalChunks += documentComponent.loadAndStore(doc.content());
        }
        return Map.of("success", true, "totalChunks", totalChunks);
    }

    /**
     * 查询文档列表
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int topK) {
        List<Document> docs = keyword != null
                ? documentComponent.search(keyword, topK)
                : documentComponent.listDocuments();
        return Map.of("total", docs.size(), "documents", docs);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public Map<String, Object> delete(@PathVariable String docId) {
        documentComponent.deleteDocument(docId);
        return Map.of("success", true);
    }

    /**
     * 清空所有文档
     */
    @DeleteMapping
    public Map<String, Object> clearAll() {
        documentComponent.clearAll();
        return Map.of("success", true);
    }

    public record DocumentUploadRequest(String title, String content) {}
    public record BatchUploadRequest(List<DocumentUploadRequest> documents) {}
}
```

### 6.3 SessionController

```java
package com.aiops.controller;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManager sessionManager;

    @DeleteMapping("/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        sessionManager.clearSession(sessionId);
        return Map.of("success", true, "message", "会话已清空");
    }
}
```

---

## 七、开发顺序

```
Step 1: 项目骨架
  ├─ 创建 Maven 项目，pom.xml
  ├─ docker-compose.yml（Qdrant + Prometheus + Ollama + MySQL）
  ├─ application.yml
  ├─ AiopsApplication.java
  └─ AiConfig.java

Step 2: 组件迁移
  ├─ SessionManager / Conversation / InMemorySessionManager（改包名，直接复制）
  ├─ RagComponent / RagComponentImpl（改包名，VectorStore 自动切 Qdrant）
  └─ DocumentComponent / DocumentComponentImpl（改包名，去 Redis 依赖）

Step 3: Tool 层
  ├─ PrometheusService（HTTP 客户端）
  ├─ PrometheusQueryTool（Function 实现）
  └─ KnowledgeSearchTool（调 RagComponent）

Step 4: Agent 层
  ├─ ops-agent-system.st（System Prompt）
  └─ OpsAgent.java（ChatClient + Tools 注册）

Step 5: Controller 层
  ├─ ChatController（SSE 流式 + 非流式）
  ├─ DocumentController（上传/查询/删除）
  └─ SessionController（清空会话）

Step 6: 联调验证
  ├─ 启动 docker-compose
  ├─ ollama pull bge-m3
  ├─ 上传运维文档到知识库
  ├─ curl 测试 SSE 对话
  └─ 验证 Agent 能自主调 PrometheusQueryTool 和 KnowledgeSearchTool
```

---

## 八、验证标准

Phase 1 完成标准（全部通过才算完）：

| 编号 | 验证项 | 方法 |
|------|--------|------|
| V1 | docker-compose 一键启动 4 个服务 | `docker compose up -d` 全部 healthy |
| V2 | 文档上传 | `POST /api/documents` 上传一篇运维文档，返回 chunkCount > 0 |
| V3 | 文档检索 | `GET /api/documents?keyword=JVM` 返回相关文档 |
| V4 | Agent 调 Prometheus | 问"order-service 的 CPU 怎么样"，Agent 自动调用 prometheusQuery |
| V5 | Agent 调知识库 | 问"JVM Full GC 怎么排查"，Agent 自动调用 knowledgeSearch |
| V6 | Agent 组合调工具 | 问"order-service 响应慢"，Agent 先调 Prometheus 再调知识库 |
| V7 | SSE 流式 | `GET /api/chat/stream` 返回 SSE 流，逐字输出 |
| V8 | 多轮对话 | 同一 sessionId 连续问两个问题，Agent 记得上一轮的上下文 |
