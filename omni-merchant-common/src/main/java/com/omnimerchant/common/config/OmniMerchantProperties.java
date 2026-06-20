package com.omnimerchant.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OmniMerchant 自定义配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnimerchant")
public class OmniMerchantProperties {

    private LlmConfig llm = new LlmConfig();
    private PgVectorConfig pgvector = new PgVectorConfig();
    private KnowledgeConfig knowledge = new KnowledgeConfig();

    @Data
    public static class LlmConfig {
        private DeepSeekConfig deepseek = new DeepSeekConfig();
    }

    @Data
    public static class DeepSeekConfig {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
    }

    @Data
    public static class PgVectorConfig {
        private String url = "jdbc:postgresql://localhost:5432/omni_merchant";
        private String username = "omnimerchant";
        private String password;
        private String driverClassName = "org.postgresql.Driver";
        private HikariConfig hikari = new HikariConfig();

        @Data
        public static class HikariConfig {
            private int maximumPoolSize = 10;
            private int minimumIdle = 2;
            private int connectionTimeout = 5000;
        }
    }

    @Data
    public static class KnowledgeConfig {
        private EmbeddingConfig embedding = new EmbeddingConfig();
        private ChunkingConfig chunking = new ChunkingConfig();
        private RetrievalConfig retrieval = new RetrievalConfig();
        private RerankerConfig reranker = new RerankerConfig();

        @Data
        public static class EmbeddingConfig {
            private int cacheTtlHours = 1;
            private int batchSize = 20;
        }

        @Data
        public static class ChunkingConfig {
            private int defaultSize = 500;
            private int defaultOverlap = 50;
        }

        @Data
        public static class RetrievalConfig {
            private int vectorTopK = 20;
            private int bm25TopK = 20;
            private double rrfK = 60.0;
            private int rerankTopN = 10;
        }

        @Data
        public static class RerankerConfig {
            private String url = "http://localhost:8001/rerank";
            private int timeoutSeconds = 5;
        }
    }
}
