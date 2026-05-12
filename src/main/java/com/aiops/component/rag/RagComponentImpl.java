package com.aiops.component.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 组件实现类
 * 只负责向量检索，不负责生成回答（由 OpsAgent 统一处理）
 */
@Component
public class RagComponentImpl implements RagComponent {

    private final VectorStore vectorStore;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.5}")
    private double defaultSimilarityThreshold;

    public RagComponentImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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
