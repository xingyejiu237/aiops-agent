package com.aiops.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6333}")
    private int qdrantPort;

    @Bean
    public WebClient prometheusWebClient() {
        return WebClient.builder()
                .baseUrl(prometheusUrl)
                .build();
    }

    @Bean
    public QdrantClient qdrantClient() {
        // gRPC 端口是 6334，HTTP REST 端口是 6333
        return new QdrantClient(QdrantGrpcClient.newBuilder(qdrantHost, 6334, false).build());
    }
}
