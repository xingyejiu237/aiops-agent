package com.aiops.component.session;

import org.springframework.ai.chat.messages.Message;

import java.time.Duration;
import java.util.List;

/**
 * 会话管理组件接口
 * 提供多轮对话上下文管理功能
 */
public interface SessionManager {

    /**
     * 获取或创建会话
     */
    Conversation getSession(String sessionId);

    /**
     * 添加用户消息
     */
    void addUserMessage(String sessionId, String content);

    /**
     * 添加 AI 回复消息
     */
    void addAssistantMessage(String sessionId, String content);

    /**
     * 添加系统消息
     */
    void addSystemMessage(String sessionId, String content);

    /**
     * 获取最近 N 条消息（用于传给 LLM）
     */
    List<Message> getRecentMessages(String sessionId, int limit);

    /**
     * 获取会话所有消息
     */
    List<Message> getAllMessages(String sessionId);

    /**
     * 清空会话
     */
    void clearSession(String sessionId);

    /**
     * 删除会话
     */
    void deleteSession(String sessionId);

    /**
     * 设置会话过期时间
     */
    void setExpire(String sessionId, Duration duration);

    /**
     * 检查会话是否存在
     */
    boolean exists(String sessionId);
}
