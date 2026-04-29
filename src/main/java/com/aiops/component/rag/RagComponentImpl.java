package com.aiops.component.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 组件实现类
 * 封装向量检索和 LLM 生成的完整 RAG 流程
 */
@Component
public class RagComponentImpl implements RagComponent {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.5}")
    private double defaultSimilarityThreshold;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是运维知识库助手。根据以下【知识库内容】回答用户问题。

            【回答规则】
            1. 基于提供的【知识库内容】回答问题
            2. 如果知识库中没有相关信息，直接说明"根据知识库，我找不到相关信息"
            3. 不要编造任何不在知识库中的内容
            4. 回答要简洁实用，直接给出解决方案

            【知识库内容】：
            %s
            """;

    public RagComponentImpl(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    @Override
    public String query(String query) {
        return query(query, null);
    }

    @Override
    public String query(String query, String customSystemPrompt) {
        List<String> contexts = retrieve(query);

        if (contexts.isEmpty()) {
            return "知识库中未找到相关信息，请先上传文档到知识库。";
        }

        String context = String.join("\n\n", contexts);

        String systemPrompt = customSystemPrompt != null
                ? customSystemPrompt.formatted(context)
                : DEFAULT_SYSTEM_PROMPT.formatted(context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
    }

    @Override
    public List<String> retrieve(String query, int topK) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(defaultSimilarityThreshold)
                        .build()
        );

        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> retrieve(String query) {
        return retrieve(query, defaultTopK);
    }
}
