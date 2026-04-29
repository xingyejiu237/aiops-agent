package com.aiops.tool;

import com.aiops.component.rag.RagComponent;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具
 * 从运维知识库中检索排障方案和SOP文档
 */
@Component
public class KnowledgeTools {

    private final RagComponent ragComponent;

    public KnowledgeTools(RagComponent ragComponent) {
        this.ragComponent = ragComponent;
    }

    @Tool(description = "从运维知识库中检索排障方案、SOP文档、运维手册等。当需要查找故障处理方案、最佳实践、配置规范等知识性内容时使用此工具。")
    public String knowledgeSearch(
            @ToolParam(description = "检索关键词或问题描述，如 'JVM Full GC调优'、'MySQL慢查询排查'、'Redis内存优化'") String query,
            @ToolParam(description = "返回结果数量，默认5") Integer topK
    ) {
        int k = (topK != null && topK > 0) ? topK : 5;
        var results = ragComponent.retrieve(query, k);

        if (results.isEmpty()) {
            return "知识库中未找到与 \"" + query + "\" 相关的内容";
        }

        return String.join("\n---\n", results);
    }
}
