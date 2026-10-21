package com.travel.diary.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
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
 * key 缺失时只是不装配 ChatModel(嵌入仍可用),不会拖垮整个应用。
 */
@Slf4j
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
     *
     * <p>key 只读环境变量 DEEPSEEK_API_KEY;缺失时不装配这个 Bean,
     * 而不是让应用起不来 —— 嵌入走本机 Ollama,和 DeepSeek 无关,
     * 只想用语义搜索的部署不该被一个没配的对话 key 卡住。
     * 配好 key 后 Bean 会照常出现,对话类功能无需改代码。
     */
    @Bean
    @ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText(T(java.lang.System).getenv('DEEPSEEK_API_KEY'))")
    public ChatModel chatModel(RestClient.Builder restClientBuilder) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
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

    /** 把降级状态说清楚:否则对话接口报"未启用"时,没人知道只是少了个环境变量。 */
    @PostConstruct
    void logChatAvailability() {
        if (!StringUtils.hasText(System.getenv("DEEPSEEK_API_KEY"))) {
            log.warn("未设置 DEEPSEEK_API_KEY:对话类 AI 功能不可用;语义向量(嵌入)不受影响");
        }
    }
}
