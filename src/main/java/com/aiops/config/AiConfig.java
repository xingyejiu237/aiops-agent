package com.aiops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    /**
     * 指定 DeepSeek 为默认 ChatModel（Ollama 只用于 Embedding）
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("deepSeekChatModel") ChatModel deepSeekChatModel) {
        return deepSeekChatModel;
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
