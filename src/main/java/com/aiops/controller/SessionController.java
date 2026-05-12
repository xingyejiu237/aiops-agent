package com.aiops.controller;

import com.aiops.component.session.SessionManager;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManager sessionManager;

    public SessionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 清空会话
     */
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        sessionManager.clearSession(sessionId);
        return Map.of("success", true);
    }
}
