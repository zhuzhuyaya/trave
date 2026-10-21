package com.travel.diary.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AI 模型的手动装配(替代被 spring.ai.model.chat/embedding=none 关掉的自动配置)。
 *
 * <p>{@code panel.ai.enabled=true} 时才创建 Ollama/DeepSeek 的模型 Bean ——
 * 自动配置做不到"key 缺失时安静降级",DeepSeek 那边是启动即抛。
 * enabled=false 时整个应用照常启动,只是所有 AI 功能不可用。
 *
 * <p>DeepSeek 的 API key 走标准环境变量 DEEPSEEK_API_KEY(官方 starter 同款来源),
 * 不另起配置项,就不会出现"yml 关了开关 key 却还在进程环境里"的灰色状态。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "panel.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    private final AiProperties aiProperties;

    /**
     * 嵌入模型:Ollama 托管的 bge-m3。
     * 长文本走 Ollama 自己的上下文截断;BGE-M3 支持 8192 token,整篇日记放得下。
     */
    @Bean
    public EmbeddingModel embeddingModel(RestClient.Builder restClientBuilder) {
        var api = OllamaApi.builder()
                .baseUrl(aiProperties.getOllamaBase())
                .restClientBuilder(restClientBuilder)
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(aiProperties.getEmbeddingModel())
                        .build())
                .build();
    }

    /**
     * 对话模型:DeepSeek。
     * 官方 starter 的工厂被 yml 关掉后,这里按同规格手动构造。
     * key 缺失时抛出 —— 但只会发生在显式开启 AI 又不给 key 的部署里。
     */
    @Bean
    public ChatModel chatModel(RestClient.Builder restClientBuilder) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "panel.ai.enabled=true 但未设置 DEEPSEEK_API_KEY 环境变量");
        }
        var api = DeepSeekApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT)
                        .maxTokens(aiProperties.getMaxTokens())
                        .build())
                .build();
    }
}
