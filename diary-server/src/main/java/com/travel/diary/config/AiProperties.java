package com.travel.diary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 能力的总开关与参数。
 *
 * <p>面板里所有 AI 入口(语义搜索/相关推荐/自动标签/年度回顾/RAG 问答)
 * 都先看 {@code enabled}:DeepSeek 没 key 或 Ollama 没起时,
 * 功能整体降级为不可用,而不是让一系列接口在运行期一个个报 500。
 */
@Data
@Component
@ConfigurationProperties(prefix = "panel.ai")
public class AiProperties {

    /** AI 总开关。false 时 ChatModel/EmbeddingModel 都不装配,AI 接口统一返回未启用。 */
    private boolean enabled = false;

    /** 嵌入模型常驻的 Ollama 地址。Ollama 是 Go 单进程,部署即跑,不依赖 Python。 */
    private String ollamaBase = "http://localhost:11434";

    /** Ollama 里的嵌入模型名(bge-m3 由 ollama pull 提供)。 */
    private String embeddingModel = "bge-m3";

    /** 语义搜索召回条数上限。个人日记量级下 20 已远超一屏。 */
    private int searchLimit = 20;

    /** 相似度阈值:余弦(归一化点积)低于它的召回视为不相关,直接不出。 */
    private double minScore = 0.45;

    /** RAG 问答喂给模型的上下文条数。 */
    private int chatContextSize = 8;

    /** DeepSeek 每次问答/回顾的生成 token 上限。 */
    private int maxTokens = 2000;
}
