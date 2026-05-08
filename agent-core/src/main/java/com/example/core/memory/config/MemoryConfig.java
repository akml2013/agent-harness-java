package com.example.core.memory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.core.mapper.AgentChatMessageMapper;
import com.example.core.mapper.AgentOperationDetailMapper;
import com.example.core.mapper.AgentSessionSummaryMapper;
import com.example.core.memory.MemoryLayer;
import com.example.core.memory.impl.MemoryLayerImpl;
import com.example.core.memory.service.EmbeddingService;
import com.example.core.memory.service.SimpleEmbeddingService;
import com.example.core.memory.service.SummaryCompressService;
import com.example.core.memory.service.impl.SiliconFlowEmbeddingService;
import com.example.core.memory.store.impl.LongTermMemoryStore;
import com.example.core.memory.store.impl.ShortTermMemoryStore;
import com.example.core.service.DeepseekService;
import com.example.core.service.SseEmitterService;

@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

    @Bean
    public StringRedisTemplate memoryRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public EmbeddingService embeddingService(MemoryProperties properties) {
        MemoryProperties.Embedding embeddingConfig = properties.getEmbedding();
        if ("siliconflow".equalsIgnoreCase(embeddingConfig.getProvider())) {
            return new SiliconFlowEmbeddingService(
                    embeddingConfig.getApiUrl(),
                    embeddingConfig.getApiKey(),
                    embeddingConfig.getModel(),
                    embeddingConfig.getDimension());
        }
        return new SimpleEmbeddingService();
    }

    @Bean
    public ShortTermMemoryStore shortTermMemoryStore(StringRedisTemplate memoryRedisTemplate,
            AgentChatMessageMapper chatMessageMapper,
            AgentOperationDetailMapper operationDetailMapper,
            MemoryProperties properties) {
        return new ShortTermMemoryStore(memoryRedisTemplate, chatMessageMapper,
                operationDetailMapper, properties);
    }

    @Bean
    public LongTermMemoryStore longTermMemoryStore(StringRedisTemplate memoryRedisTemplate,
            AgentChatMessageMapper chatMessageMapper,
            AgentSessionSummaryMapper summaryMapper,
            MemoryProperties properties) {
        return new LongTermMemoryStore(memoryRedisTemplate, chatMessageMapper,
                summaryMapper, properties);
    }

    @Bean
    public SummaryCompressService summaryCompressService(DeepseekService deepseekService,
            MemoryProperties properties) {
        return new SummaryCompressService(deepseekService, properties);
    }

    @Bean
    public MemoryLayer memoryLayer(ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryStore longTermMemoryStore,
            SummaryCompressService summaryCompressService,
            MemoryProperties properties,
            SseEmitterService sseEmitterService) {
        MemoryLayerImpl impl = new MemoryLayerImpl(shortTermMemoryStore, longTermMemoryStore,
                summaryCompressService, properties);
        impl.setSseEmitterService(sseEmitterService);
        return impl;
    }
}
