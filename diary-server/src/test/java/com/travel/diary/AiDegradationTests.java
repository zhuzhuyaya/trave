package com.travel.diary;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 可降级装配的回归测试。
 *
 * <p>嵌入走本机 Ollama,对话走 DeepSeek,两者的可用性互相独立:
 * 只想要语义搜索的部署没有 DEEPSEEK_API_KEY 是常态,
 * 不能因此让整个应用起不来。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "panel.ai.enabled=true")
class AiDegradationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("开启 AI 但没配 DeepSeek key:应用照常启动,嵌入模型可用")
    void startsWithoutDeepSeekKey() {
        Assumptions.assumeTrue(!StringUtils.hasText(System.getenv("DEEPSEEK_API_KEY")),
                "本机设置了 DEEPSEEK_API_KEY,跳过缺 key 的降级用例");

        // 嵌入模型照常装配(建 Bean 不发起网络请求,不依赖 Ollama 是否已启动)
        assertThat(context.getBeansOfType(EmbeddingModel.class)).isNotEmpty();
        // 关键断言:缺 key 时是"没有 ChatModel Bean",而不是启动时抛异常
        assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
    }
}
