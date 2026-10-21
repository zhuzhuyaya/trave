package com.travel.diary.service;

import java.util.List;

/**
 * 日记的语义向量维护与检索。
 *
 * <p>写入侧:日记保存/删除时同步"插入或重嵌或删除"一条向量;
 * 启动时对本库做一次全量回填(已被 text_hash 跳过的重算成本为零)。
 * 读侧给语义搜索一个"全量向量 + 余弦"的暴力检索 —— 个人日记量级下
 * (几百~几千篇,向量常驻内存几 MB)比任何索引都简单且够快,
 * 真到了十万篇再换 pgvector,接口形状不变。
 */
public interface EmbeddingService {

    /**
     * 嵌入能力是否可用:AI 总开关打开且嵌入模型 Bean 已装配。
     * 不探活 —— Ollama 进程挂了要等真正调用时才暴露。
     */
    boolean available();

    /** 保存/更新日记后调用:内容变了就重嵌,没变(text_hash 相同)就跳过。失败不影响主流程。 */
    void sync(Long entryId);

    /** 删除日记时调用,把向量一并清掉。失败不影响主流程。 */
    void remove(Long entryId);

    /**
     * 启动回填:把还没有向量(或哈希已过期)的日记补齐。
     * AI 关闭时只打一行日志,什么都不做;任何失败都不阻塞启动。
     */
    void backfillAll();

    /**
     * 语义搜索:把查询也嵌入一次,与当前用户全部向量算余弦,取 top-N。
     *
     * @return entryId → 相似度,已按相似度降序、过滤掉低于阈值的
     */
    List<Scored> search(Long userId, String query);

    /** 检索命中项。score 是余弦相似度(0~1),给前端做"相关度"展示可选。 */
    record Scored(Long entryId, double score) {
    }
}
