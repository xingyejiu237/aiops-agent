package com.aiops.component.rag;

import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) 组件接口
 * 提供标准化的检索增强生成功能
 */
public interface RagComponent {

    /**
     * 执行 RAG 查询
     *
     * @param query 用户查询
     * @return RAG 回答
     */
    String query(String query);

    /**
     * 执行 RAG 查询，带自定义系统提示
     *
     * @param query        用户查询
     * @param systemPrompt 自定义系统提示（覆盖默认提示）
     * @return RAG 回答
     */
    String query(String query, String systemPrompt);

    /**
     * 检索相关文档（不生成回答）
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
