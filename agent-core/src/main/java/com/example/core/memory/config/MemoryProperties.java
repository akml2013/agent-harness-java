package com.example.core.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    private ShortTerm shortTerm = new ShortTerm();
    private LongTerm longTerm = new LongTerm();
    private Context context = new Context();
    private Compress compress = new Compress();
    private React react = new React();
    private Embedding embedding = new Embedding();

    @Data
    public static class ShortTerm {
        private int ttl = 600;
        private int maxRounds = 20;
        private int resultTruncateLength = 200;
    }

    @Data
    public static class LongTerm {
        private boolean enabled = true;
        private int effectiveConversationCount = 10;
        private int summaryTokenThreshold = 4000;
        private int summaryCacheTtl = 3600;
    }

    @Data
    public static class Context {
        private int maxTokens = 8000;
        private int outputReserveTokens = 2000;
    }

    @Data
    public static class Compress {
        private int fallbackTruncateLength = 200;
        private int llmTimeoutSeconds = 30;
        private int awaitTimeoutSeconds = 60;
    }

    @Data
    public static class React {
        private int defaultMaxRounds = 10;
        private int continueAddRounds = 10;
    }

    @Data
    public static class Embedding {
        private String provider = "simple";
        private String apiUrl = "";
        private String apiKey = "";
        private String model = "";
        private int dimension = 128;
    }

    public int getAvailableTokens() {
        return context.getMaxTokens() - context.getOutputReserveTokens();
    }

    public int getN() {
        return shortTerm.getMaxRounds();
    }

    public int getEffectiveConversationCount() {
        return longTerm.getEffectiveConversationCount();
    }
}
