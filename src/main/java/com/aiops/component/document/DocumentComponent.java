package com.aiops.component.document;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档处理组件接口
 * 提供文档加载、分割、存储功能
 */
public interface DocumentComponent {

    /**
     * 从字符串加载文档（自动分割）
     */
    List<Document> loadFromString(String content);

    /**
     * 从字符串加载文档（指定分隔符）
     */
    List<Document> loadFromString(String content, String delimiter);

    /**
     * 存储文档到向量库
     */
    void store(List<Document> documents);

    /**
     * 加载并存储（便捷方法）
     */
    int loadAndStore(String content);

    /**
     * 列出所有文档
     */
    List<Document> listDocuments();

    /**
     * 删除指定文档
     */
    void deleteDocument(String docId);

    /**
     * 清空所有文档
     */
    void clearAll();

    /**
     * 获取文档数量
     */
    long count();
}
