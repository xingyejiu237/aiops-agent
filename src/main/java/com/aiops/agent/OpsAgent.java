package com.aiops.agent;

import com.aiops.component.session.SessionManager;
import com.aiops.tool.KnowledgeTools;
import com.aiops.tool.PrometheusTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 运维 Agent — 单 Agent + ReAct 推理
 * 通过 ChatClient 注册 @Tool 注解的工具，Spring AI 自动处理 ReAct 循环
 */
@Component
public class OpsAgent {

    private static final Logger log = LoggerFactory.getLogger(OpsAgent.class);

    private final ChatClient chatClient;
    private final SessionManager sessionManager;
    private final PrometheusTools prometheusTools;
    private final KnowledgeTools knowledgeTools;

    @Value("${session.max-history:20}")
    private int maxHistory;

    private static final String SYSTEM_PROMPT = """
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
            - 不要在回答中输出你的思考过程，例如"让我查一下"、"我来帮你"、"查询失败了"等过渡性语句，直接给出最终分析结果

            ## 语言
            - 使用中文回答
            """;

    public OpsAgent(ChatClient chatClient,
                    SessionManager sessionManager,
                    PrometheusTools prometheusTools,
                    KnowledgeTools knowledgeTools) {
        this.chatClient = chatClient;
        this.sessionManager = sessionManager;
        this.prometheusTools = prometheusTools;
        this.knowledgeTools = knowledgeTools;
    }

    /**
     * SSE 流式对话
     */
    public Flux<String> chatStream(String message, String sessionId) {
        log.info("[OpsAgent] 收到消息, sessionId={}, message={}", sessionId, message);

        // 保存用户消息到会话
        sessionManager.addUserMessage(sessionId, message);

        // 获取历史消息
        List<Message> history = sessionManager.getRecentMessages(sessionId, maxHistory);

        // 构建流式请求，注册 @Tool 注解的工具对象
        Flux<String> responseFlux = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(message)
                .tools(prometheusTools, knowledgeTools)
                .stream()
                .content();

        // 收集完整回答保存到会话（不影响流式返回前端）
        StringBuilder fullResponse = new StringBuilder();
        return responseFlux
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    String answer = fullResponse.toString();
                    if (!answer.isBlank()) {
                        sessionManager.addAssistantMessage(sessionId, answer);
                        log.info("[OpsAgent] 回答已保存, sessionId={}, length={}", sessionId, answer.length());
                    }
                })
                .doOnError(e -> log.error("[OpsAgent] 流式响应错误: {}", e.getMessage()));
    }

    /**
     * 非流式对话（备用/调试用）
     */
    public String chat(String message, String sessionId) {
        log.info("[OpsAgent] 收到消息(非流式), sessionId={}, message={}", sessionId, message);

        sessionManager.addUserMessage(sessionId, message);
        List<Message> history = sessionManager.getRecentMessages(sessionId, maxHistory);

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(message)
                .tools(prometheusTools, knowledgeTools)
                .call()
                .content();

        sessionManager.addAssistantMessage(sessionId, answer);
        log.info("[OpsAgent] 回答完成, sessionId={}, length={}", sessionId, answer.length());
        return answer;
    }
}
