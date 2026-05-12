package com.aiops.tool;

import com.aiops.service.PrometheusService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Prometheus 指标查询工具
 * 通过 @Tool 注解注册为 Spring AI 可调用的工具
 */
@Component
public class PrometheusTools {

    private final PrometheusService prometheusService;

    public PrometheusTools(PrometheusService prometheusService) {
        this.prometheusService = prometheusService;
    }

    @Tool(description = "查询 Prometheus 监控指标，获取服务的 CPU 使用率、内存使用率、QPS、响应延迟等运行数据。当用户询问服务运行状况、性能指标、是否卡顿等问题时使用此工具。当前可用的服务名(job)为: aiops-agent")
    public String prometheusQuery(
            @ToolParam(description = "服务名称(即Prometheus的job名)，当前可用: aiops-agent") String service,
            @ToolParam(description = "指标类型，可选值: cpu_usage(CPU使用率), memory_usage(内存使用), qps(每秒请求数), latency(响应延迟P95), error_rate(错误率), gc_count(GC次数)") String metric,
            @ToolParam(description = "查询时间范围，如 5m(5分钟), 30m(30分钟), 1h(1小时), 6h(6小时), 24h(24小时)，默认1h") String duration
    ) {
        String dur = (duration == null || duration.isBlank()) ? "1h" : duration;
        String promql = buildPromQL(service, metric);
        return prometheusService.queryRange(promql, dur);
    }

    /**
     * 根据指标类型构建 PromQL 表达式
     * 指标名基于 Spring Boot Actuator (Micrometer) 暴露的 Prometheus 指标
     */
    private String buildPromQL(String service, String metric) {
        if (metric == null) metric = "";

        return switch (metric.toLowerCase()) {
            case "cpu_usage" ->
                    "process_cpu_usage{job=\"" + service + "\"}";
            case "memory_usage" ->
                    "sum(jvm_memory_used_bytes{job=\"" + service + "\",area=\"heap\"}) / sum(jvm_memory_max_bytes{job=\"" + service + "\",area=\"heap\"}) * 100";
            case "qps" ->
                    "sum(rate(http_server_requests_seconds_count{job=\"" + service + "\"}[5m]))";
            case "latency" ->
                    "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"" + service + "\"}[5m])) by (le))";
            case "error_rate" ->
                    "sum(rate(http_server_requests_seconds_count{job=\"" + service + "\",status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count{job=\"" + service + "\"}[5m])) * 100";
            case "gc_count" ->
                    "sum(rate(jvm_gc_pause_seconds_count{job=\"" + service + "\"}[5m])) by (cause)";
            default ->
                    "up{job=\"" + service + "\"}";
        };
    }
}
