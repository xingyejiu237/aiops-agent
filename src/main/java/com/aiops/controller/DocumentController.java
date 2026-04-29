package com.aiops.controller;

import com.aiops.component.document.DocumentComponent;
import com.aiops.component.document.DocumentComponentImpl;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文档管理接口
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentComponent documentComponent;
    private final VectorStore vectorStore;

    public DocumentController(DocumentComponent documentComponent, VectorStore vectorStore) {
        this.documentComponent = documentComponent;
        this.vectorStore = vectorStore;
    }

    /**
     * 上传文档
     */
    @PostMapping
    public Map<String, Object> upload(@RequestBody DocumentUploadRequest request) {
        List<Document> documents = ((DocumentComponentImpl) documentComponent)
                .loadFromString(request.content(), "\\n\\n", request.title());
        documentComponent.store(documents);
        return Map.of("success", true, "chunkCount", documents.size());
    }

    /**
     * 批量上传
     */
    @PostMapping("/batch")
    public Map<String, Object> batchUpload(@RequestBody BatchUploadRequest request) {
        int totalChunks = 0;
        for (var doc : request.documents()) {
            List<Document> documents = ((DocumentComponentImpl) documentComponent)
                    .loadFromString(doc.content(), "\\n\\n", doc.title());
            documentComponent.store(documents);
            totalChunks += documents.size();
        }
        return Map.of("success", true, "totalChunks", totalChunks);
    }

    /**
     * 查询文档列表（通过向量相似度检索）
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int topK) {
        List<Document> docs;
        if (keyword != null && !keyword.isBlank()) {
            docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(keyword).topK(topK).build()
            );
        } else {
            docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query("所有文档").topK(topK).similarityThreshold(0.0).build()
            );
        }
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
