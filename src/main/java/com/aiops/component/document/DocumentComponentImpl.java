package com.aiops.component.document;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档处理组件实现
 * 基于 Qdrant VectorStore，不依赖 Redis
 */
@Component
public class DocumentComponentImpl implements DocumentComponent {

    private final VectorStore vectorStore;
    private final QdrantClient qdrantClient;

    private static final String DEFAULT_DELIMITER = "\\n\\n";

    public DocumentComponentImpl(VectorStore vectorStore, QdrantClient qdrantClient) {
        this.vectorStore = vectorStore;
        this.qdrantClient = qdrantClient;
    }

    @Override
    public List<Document> loadFromString(String content) {
        return loadFromString(content, DEFAULT_DELIMITER, null);
    }

    /**
     * 带标题的文档分片，标题会被拼到每个分片前面，提高检索命中率
     */
    public List<Document> loadFromString(String content, String delimiter, String title) {
        String regex = delimiter.replace("\\n", "\n");
        return Arrays.stream(content.split(regex))
                .filter(segment -> !segment.trim().isEmpty())
                .map(segment -> {
                    String text = (title != null && !title.isBlank())
                            ? title + "\n" + segment.trim()
                            : segment.trim();
                    return new Document(text);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Document> loadFromString(String content, String delimiter) {
        return loadFromString(content, delimiter, null);
    }

    @Override
    public void store(List<Document> documents) {
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    @Override
    public int loadAndStore(String content) {
        List<Document> documents = loadFromString(content);
        store(documents);
        return documents.size();
    }

    @Override
    public List<Document> listDocuments() {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("所有文档内容")
                        .topK(100)
                        .similarityThreshold(0.0)
                        .build()
        );
    }

    @Override
    public void deleteDocument(String docId) {
        vectorStore.delete(List.of(docId));
    }

    @Override
    public void clearAll() {
        try {
            qdrantClient.deleteCollectionAsync("aiops_knowledge").get();
            qdrantClient.createCollectionAsync(
                    Collections.CreateCollection.newBuilder()
                            .setCollectionName("aiops_knowledge")
                            .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                                    .setParams(Collections.VectorParams.newBuilder()
                                            .setSize(1024)
                                            .setDistance(Collections.Distance.Cosine)
                                            .build())
                                    .build())
                            .build()
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("清空向量库失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        try {
            var info = qdrantClient.getCollectionInfoAsync("aiops_knowledge").get();
            return info.getPointsCount();
        } catch (Exception e) {
            return 0;
        }
    }
}
