package com.aiops.benchmark;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.LongStream;

/**
 * AIOps Agent 基准测试
 *
 * 使用方式：
 * 1. 先启动应用: mvn spring-boot:run
 * 2. 运行本测试: java src/test/java/com/aiops/benchmark/Benchmark.java
 *    或编译后: java -cp target/classes com.aiops.benchmark.Benchmark
 *
 * 测试指标：
 * - 端到端响应时间
 * - 工具调用分类（指标类/知识类/通用类）
 * - SSE 首 token 延迟
 */
public class Benchmark {

    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 测试用例：类型 + 问题
    private static final List<TestCase> TEST_CASES = List.of(
            // 指标类问题（应触发 Prometheus 工具）
            new TestCase("metric", "aiops-agent 服务的 CPU 使用率是多少？"),
            new TestCase("metric", "查看 aiops-agent 的内存使用情况"),
            new TestCase("metric", "aiops-agent 的 QPS 是多少？"),
            new TestCase("metric", "aiops-agent 的 P95 延迟是多少？"),
            new TestCase("metric", "最近 1 小时 aiops-agent 有没有异常？"),

            // 知识类问题（应触发 Knowledge 工具）
            new TestCase("knowledge", "JVM Full GC 怎么排查？"),
            new TestCase("knowledge", "MySQL 慢查询如何优化？"),
            new TestCase("knowledge", "Redis 内存满了怎么办？"),
            new TestCase("knowledge", "服务频繁重启的可能原因有哪些？"),
            new TestCase("knowledge", "OOM 的常见排查步骤是什么？"),

            // 混合类问题（可能触发双工具）
            new TestCase("hybrid", "aiops-agent 服务 CPU 飙高怎么排查？"),
            new TestCase("hybrid", "如果 aiops-agent 内存持续增长，可能是什么原因？"),
            new TestCase("hybrid", "aiops-agent 响应变慢了，帮我排查一下"),

            // 通用类问题（不触发工具）
            new TestCase("general", "你好，你能做什么？"),
            new TestCase("general", "今天天气怎么样？")
    );

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       AIOps Agent 基准测试                   ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // 1. 健康检查
        System.out.println("━━━ 健康检查 ━━━");
        boolean healthy = checkHealth();
        if (!healthy) {
            System.err.println("❌ 应用未启动或不可用，请先运行: mvn spring-boot:run");
            return;
        }
        System.out.println("✅ 应用正常运行");
        System.out.println();

        // 2. 运行基准测试
        System.out.println("━━━ 基准测试（共 " + TEST_CASES.size() + " 个用例）━━━");
        System.out.println();

        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < TEST_CASES.size(); i++) {
            TestCase tc = TEST_CASES.get(i);
            System.out.printf("[%d/%d] 测试中: %s...%n", i + 1, TEST_CASES.size(), tc.question());
            TestResult result = runTest(tc);
            results.add(result);
            System.out.printf("  → 耗时: %.1fs | 响应长度: %d 字%n",
                    result.durationMs() / 1000.0, result.responseLength());
            // 间隔避免限流
            Thread.sleep(2000);
        }

        // 3. SSE 首 token 延迟测试
        System.out.println();
        System.out.println("━━━ SSE 首 token 延迟测试 ━━━");
        List<Double> sseLatencies = testSseLatency();

        // 4. 输出报告
        System.out.println();
        printReport(results, sseLatencies);
    }

    private static boolean checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static TestResult runTest(TestCase tc) {
        String jsonBody = """
                {"sessionId":"bench-%s","message":"%s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), tc.question().replace("\"", "\\\""));

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;
            return new TestResult(tc, duration, response.body(), response.statusCode() == 200);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new TestResult(tc, duration, e.getMessage(), false);
        }
    }

    private static List<Double> testSseLatency() {
        List<Double> latencies = new ArrayList<>();
        String[] sseTestQuestions = {
                "aiops-agent 的 CPU 使用率是多少？",
                "JVM Full GC 怎么排查？",
                "aiops-agent 服务 CPU 飙高怎么排查？"
        };

        for (String question : sseTestQuestions) {
            try {
                String url = BASE_URL + "/api/chat/stream?sessionId=sse-bench&message=" +
                        java.net.URLEncoder.encode(question, "UTF-8");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(60))
                        .GET().build();

                long start = System.currentTimeMillis();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                // SSE 首 token：找第一个非空 data 行
                double firstTokenMs = -1;
                for (String line : body.split("\n")) {
                    if (line.startsWith("data:") && !line.contains("[DONE]")) {
                        String data = line.substring(5).trim();
                        if (!data.isEmpty()) {
                            firstTokenMs = (System.currentTimeMillis() - start);
                            break;
                        }
                    }
                }

                if (firstTokenMs > 0) {
                    latencies.add(firstTokenMs);
                    System.out.printf("  问题: \"%s\" → 首 token: %.1fs%n", question, firstTokenMs / 1000.0);
                } else {
                    System.out.printf("  问题: \"%s\" → 未获取到 SSE 数据%n", question);
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.printf("  问题: \"%s\" → 测试失败: %s%n", question, e.getMessage());
            }
        }
        return latencies;
    }

    private static void printReport(List<TestResult> results, List<Double> sseLatencies) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║              基准测试报告                    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // 按类型分组统计
        Map<String, List<TestResult>> grouped = new LinkedHashMap<>();
        grouped.put("metric", new ArrayList<>());
        grouped.put("knowledge", new ArrayList<>());
        grouped.put("hybrid", new ArrayList<>());
        grouped.put("general", new ArrayList<>());
        for (TestResult r : results) {
            grouped.get(r.testCase().type()).add(r);
        }

        // 成功率
        long successCount = results.stream().filter(TestResult::success).count();
        System.out.printf("总成功率: %d/%d (%.0f%%)%n", successCount, results.size(), successCount * 100.0 / results.size());
        System.out.println();

        // 各类型响应时间
        System.out.println("── 各类型响应时间 ──");
        String[] typeNames = {"metric", "knowledge", "hybrid", "general"};
        String[] typeLabels = {"指标查询(Prometheus)", "知识检索(RAG)", "混合查询(双工具)", "通用问答"};
        for (int i = 0; i < typeNames.length; i++) {
            List<TestResult> group = grouped.get(typeNames[i]);
            if (group.isEmpty()) continue;
            LongSummaryStatistics stats = group.stream()
                    .filter(TestResult::success)
                    .mapToLong(TestResult::durationMs)
                    .summaryStatistics();
            if (stats.getCount() == 0) {
                System.out.printf("  %-24s 无成功响应%n", typeLabels[i]);
                continue;
            }
            System.out.printf("  %-24s 平均: %.1fs | 最快: %.1fs | 最慢: %.1fs | 样本: %d%n",
                    typeLabels[i],
                    stats.getAverage() / 1000.0,
                    stats.getMin() / 1000.0,
                    stats.getMax() / 1000.0,
                    stats.getCount());
        }

        // 全局统计
        System.out.println();
        LongSummaryStatistics allStats = results.stream()
                .filter(TestResult::success)
                .mapToLong(TestResult::durationMs)
                .summaryStatistics();
        System.out.printf("── 全局统计 ──%n");
        System.out.printf("  端到端平均响应: %.1fs%n", allStats.getAverage() / 1000.0);
        System.out.printf("  P95 响应时间:   %.1fs%n", percentile(results.stream().filter(TestResult::success).mapToLong(TestResult::durationMs).sorted().toArray(), 95) / 1000.0);

        // SSE 延迟
        if (!sseLatencies.isEmpty()) {
            System.out.println();
            DoubleSummaryStatistics sseStats = sseLatencies.stream().mapToDouble(d -> d).summaryStatistics();
            System.out.printf("── SSE 首 token 延迟 ──%n");
            System.out.printf("  平均: %.1fs | 最快: %.1fs | 最慢: %.1fs%n",
                    sseStats.getAverage() / 1000.0,
                    sseStats.getMin() / 1000.0,
                    sseStats.getMax() / 1000.0);
        }

        // 简历可用摘要
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║         简历可用量化数据                      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.printf("  端到端平均响应: %.1fs%n", allStats.getAverage() / 1000.0);
        System.out.printf("  指标类平均: %.1fs | 知识类平均: %.1fs%n",
                avgOf(grouped.get("metric")), avgOf(grouped.get("knowledge")));
        System.out.printf("  混合类(双工具)平均: %.1fs%n", avgOf(grouped.get("hybrid")));
        if (!sseLatencies.isEmpty()) {
            System.out.printf("  SSE 首 token: %.1fs%n", sseLatencies.stream().mapToDouble(d -> d).average().orElse(0) / 1000.0);
        }
        System.out.printf("  测试用例总数: %d | 成功率: %.0f%%%n", results.size(), successCount * 100.0 / results.size());
    }

    private static double avgOf(List<TestResult> list) {
        return list.stream().filter(TestResult::success)
                .mapToLong(TestResult::durationMs).average().orElse(0) / 1000.0;
    }

    private static double percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // ─── 数据类 ───

    record TestCase(String type, String question) {}
    record TestResult(TestCase testCase, long durationMs, String response, boolean success) {
        int responseLength() {
            return response != null ? response.length() : 0;
        }
    }
}
