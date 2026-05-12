package com.aiops.component.session;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话实体类
 * 存储会话的元数据和消息历史
 */
public class Conversation {

    private final String sessionId;
    private final List<Message> messages;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;

    public Conversation(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public void addUserMessage(String content) {
        messages.add(new UserMessage(content));
        updateTime();
    }

    public void addAssistantMessage(String content) {
        messages.add(new AssistantMessage(content));
        updateTime();
    }

    public void addSystemMessage(String content) {
        messages.add(new SystemMessage(content));
        updateTime();
    }

    public List<Message> getRecentMessages(int limit) {
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public void clear() {
        messages.clear();
        updateTime();
    }

    public int getMessageCount() {
        return messages.size();
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    private void updateTime() {
        this.lastUpdateTime = LocalDateTime.now();
    }
}
