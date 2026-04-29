package com.aiops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Prometheus HTTP 客户端服务
 */
@Service
public class PrometheusService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PrometheusService(WebClient prometheusWebClient, ObjectMapper objectMapper) {
        this.webClient = prometheusWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 PromQL 范围查询
     */
    public String queryRange(String promql, String duration) {
        long end = System.currentTimeMillis() / 1000;
        long start = end - parseDuration(duration);
        long step = Math.max(60, (end - start) / 100);

        log.debug("[Prometheus] queryRange PromQL={}", promql);

        try {
            // PromQL 含花括号如 {job="xxx"}，必须用 URI 模板变量传入，
            // 否则 UriBuilder 会把 {job="xxx"} 当成模板变量展开导致报错
            String json = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query_range")
                            .queryParam("query", "{promql}")
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("step", step)
                            .build(promql))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return formatPrometheusResponse(json, promql);
        } catch (Exception e) {
            log.error("Prometheus query failed: {}", e.getMessage());
            return "查询 Prometheus 失败: " + e.getMessage();
        }
    }

    /**
     * 执行 PromQL 即时查询
     */
    public String queryInstant(String promql) {
        log.debug("[Prometheus] queryInstant PromQL={}", promql);

        try {
            String json = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/query")
                            .queryParam("query", "{promql}")
                            .build(promql))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return formatPrometheusResponse(json, promql);
        } catch (Exception e) {
            log.error("Prometheus instant query failed: {}", e.getMessage());
            return "查询 Prometheus 失败: " + e.getMessage();
        }
    }

    /**
     * 格式化 Prometheus JSON 响应为人类可读摘要
     */
    private String formatPrometheusResponse(String json, String promql) {
        if (json == null || json.isEmpty()) {
            return "Prometheus 返回空结果";
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                String error = root.path("error").asText("未知错误");
                return "Prometheus 查询错误: " + error;
            }

            JsonNode result = root.path("data").path("result");
            if (result.isEmpty()) {
                return "没有找到匹配的指标数据 (PromQL: " + promql + ")";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Prometheus 查询结果:\n");

            for (JsonNode item : result) {
                JsonNode metric = item.path("metric");
                String instance = metric.path("instance").asText("unknown");
                String job = metric.path("job").asText("unknown");

                sb.append("  服务[").append(job).append("] 实例[").append(instance).append("]:\n");

                JsonNode values = item.path("values");
                if (values.isArray() && !values.isEmpty()) {
                    JsonNode latest = values.get(values.size() - 1);
                    double value = latest.get(1).asDouble();
                    sb.append("    当前值: ").append(formatValue(value));

                    if (values.size() > 1) {
                        JsonNode earliest = values.get(0);
                        double firstValue = earliest.get(1).asDouble();
                        double change = value - firstValue;
                        String trend = change > 0.05 ? "↑ 上升" : change < -0.05 ? "↓ 下降" : "→ 平稳";
                        sb.append(", 趋势: ").append(trend);
                    }
                    sb.append("\n");
                } else {
                    JsonNode value = item.path("value");
                    if (value.isArray() && value.size() >= 2) {
                        double val = value.get(1).asDouble();
                        sb.append("    当前值: ").append(formatValue(val)).append("\n");
                    }
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to parse Prometheus response, returning raw: {}", e.getMessage());
            return "原始响应: " + json.substring(0, Math.min(json.length(), 500));
        }
    }

    private String formatValue(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000);
        if (value < 0.01) return String.format("%.4f", value);
        return String.format("%.2f", value);
    }

    private long parseDuration(String duration) {
        if (duration == null) return 3600;
        duration = duration.trim().toLowerCase();
        if (duration.endsWith("m")) return Long.parseLong(duration.replace("m", "")) * 60;
        if (duration.endsWith("h")) return Long.parseLong(duration.replace("h", "")) * 3600;
        if (duration.endsWith("d")) return Long.parseLong(duration.replace("d", "")) * 86400;
        return 3600;
    }
}
