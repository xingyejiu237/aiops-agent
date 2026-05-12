package com.aiops.component.rag;

import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) 组件接口
 * 只负责向量检索，不负责生成回答（由 OpsAgent 统一处理）
 */
public interface RagComponent {

    /**
     * 检索相关文档
     *
     * @param query 查询文本
     * @param topK  返回文档数量
     * @return 相关文档内容列表
     */
    List<String> retrieve(String query, int topK);

    /**
     * 检索相关文档（使用默认 topK）
     *
     * @param query 查询文本
     * @return 相关文档内容列表
     */
    List<String> retrieve(String query);
}
