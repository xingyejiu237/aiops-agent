package com.aiops.component.session;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话管理实现
 * 基于 ConcurrentHashMap，适用于开发和测试环境
 */
@Component
public class InMemorySessionManager implements SessionManager {

    private final Map<String, Conversation> sessions = new ConcurrentHashMap<>();

    @Override
    public Conversation getSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, Conversation::new);
    }

    @Override
    public void addUserMessage(String sessionId, String content) {
        getSession(sessionId).addUserMessage(content);
    }

    @Override
    public void addAssistantMessage(String sessionId, String content) {
        getSession(sessionId).addAssistantMessage(content);
    }

    @Override
    public void addSystemMessage(String sessionId, String content) {
        getSession(sessionId).addSystemMessage(content);
    }

    @Override
    public List<Message> getRecentMessages(String sessionId, int limit) {
        return getSession(sessionId).getRecentMessages(limit);
    }

    @Override
    public List<Message> getAllMessages(String sessionId) {
        return getSession(sessionId).getMessages();
    }

    @Override
    public void clearSession(String sessionId) {
        getSession(sessionId).clear();
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public void setExpire(String sessionId, Duration duration) {
        // 内存实现不支持过期，可扩展为定时清理
    }

    @Override
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
