package com.aiops.controller;

import com.aiops.agent.OpsAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 对话接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OpsAgent opsAgent;

    public ChatController(OpsAgent opsAgent) {
        this.opsAgent = opsAgent;
    }

    /**
     * SSE 流式对话
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String sessionId,
            @RequestParam String message) {
        return opsAgent.chatStream(message, sessionId)
                .concatWith(Flux.just("[DONE]"));
    }

    /**
     * 非流式对话（备用/调试用）
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = opsAgent.chat(request.message(), request.sessionId());
        return Map.of(
                "sessionId", request.sessionId(),
                "answer", answer
        );
    }

    public record ChatRequest(String sessionId, String message) {}
}
