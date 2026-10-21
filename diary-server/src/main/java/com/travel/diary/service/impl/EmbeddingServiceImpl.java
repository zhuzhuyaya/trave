package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.diary.config.AiProperties;
import com.travel.diary.entity.Entry;
import com.travel.diary.entity.EntryEmbedding;
import com.travel.diary.mapper.EntryEmbeddingMapper;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 语义向量维护。
 *
 * <p>EmbeddingModel 通过 {@link ObjectProvider} 惰性取:AI 关闭时容器里根本没有
 * 这个 Bean,字段直接注入 EmbeddingModel 会让整个应用起不来 —— 而 AI 是可降级的。
 * 所有入口先问 {@link #available()},不可用时安静返回,日记主流程不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    /** 个人日记量级暴力余弦够用;这个上限只防"一次回填把 Ollama 打满",分批只为日志可读。 */
    private static final int BACKFILL_BATCH = 32;

    private final EntryMapper entryMapper;
    private final EntryEmbeddingMapper embeddingMapper;
    private final AiProperties aiProperties;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Override
    public boolean available() {
        return embeddingModelProvider.getIfAvailable() != null;
    }

    @Override
    public void sync(Long entryId) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null || entryId == null) {
            return;
        }
        try {
            Entry entry = entryMapper.selectById(entryId);
            if (entry == null) {
                // 已被删除(或事务里刚删),向量没有存在的意义
                remove(entryId);
                return;
            }
            embedAndStore(entry, model);
        } catch (Exception e) {
            // 嵌入失败不能让保存日记的请求回滚:正文已经落库,向量可以下次回填补上
            log.warn("日记 {} 向量化失败,跳过(下次启动回填会重试): {}", entryId, e.toString());
        }
    }

    @Override
    public void remove(Long entryId) {
        if (entryId == null) {
            return;
        }
        try {
            embeddingMapper.deleteById(entryId);
        } catch (Exception e) {
            log.warn("日记 {} 向量删除失败: {}", entryId, e.toString());
        }
    }

    @Override
    public void backfillAll() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            log.info("AI 未启用,跳过语义向量回填");
            return;
        }
        try {
            doBackfill(model);
        } catch (Exception e) {
            // 回填是启动期的尽力而为:表还没建、数据库瞬断都只记日志,不能让应用起不来
            log.warn("语义向量回填中止(不影响启动): {}", e.toString());
        }
    }

    private void doBackfill(EmbeddingModel model) {
        // location_name 必须一起查:embedText 会把它拼进向量文本,
        // 漏掉的话回填出来的向量与保存时的不一致,哈希也会每轮启动都对不上。
        List<Entry> entries = entryMapper.selectList(new LambdaQueryWrapper<Entry>()
                .select(Entry::getId, Entry::getUserId, Entry::getTitle,
                        Entry::getContent, Entry::getLocationName)
                .orderByAsc(Entry::getId));
        if (entries.isEmpty()) {
            return;
        }

        // 只取 entry_id + text_hash 判重;整行包含几百 KB 的向量文本,全量读进来纯属浪费
        var existing = embeddingMapper.selectList(new LambdaQueryWrapper<EntryEmbedding>()
                        .select(EntryEmbedding::getEntryId, EntryEmbedding::getTextHash)
                        .eq(EntryEmbedding::getModel, aiProperties.getEmbeddingModel()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(EntryEmbedding::getEntryId, EntryEmbedding::getTextHash));

        int embedded = 0;
        int failed = 0;
        for (int i = 0; i < entries.size(); i += BACKFILL_BATCH) {
            List<Entry> batch = entries.subList(i, Math.min(i + BACKFILL_BATCH, entries.size()));
            for (Entry entry : batch) {
                String hash = hash(embedText(entry));
                if (Objects.equals(hash, existing.get(entry.getId()))) {
                    continue;
                }
                try {
                    embedAndStore(entry, model);
                    embedded++;
                } catch (Exception e) {
                    failed++;
                    log.warn("日记 {} 回填失败: {}", entry.getId(), e.toString());
                }
            }
            log.info("语义向量回填进度 {}/{}", Math.min(i + BACKFILL_BATCH, entries.size()), entries.size());
        }
        log.info("语义向量回填完成:新嵌入 {} 篇,失败 {} 篇,共 {} 篇", embedded, failed, entries.size());
    }

    /** 应用启动完成后再回填:此时 Ollama 未必已起,失败只记日志,不阻塞启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        backfillAll();
    }

    /* ---------------------------------------------------------------------
     *  内部
     * ------------------------------------------------------------------- */

    /** 嵌入并覆盖写入。写库用 upsert 语义:先删后插在同一篇上等价于整体覆盖。 */
    private void embedAndStore(Entry entry, EmbeddingModel model) {
        String text = embedText(entry);
        if (text.isBlank()) {
            // 空日记没有语义可嵌,顺手清掉旧向量(可能正文被清空了)
            remove(entry.getId());
            return;
        }

        float[] vector = model.embed(text);
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("嵌入模型返回空向量");
        }

        EntryEmbedding row = new EntryEmbedding();
        row.setEntryId(entry.getId());
        row.setUserId(entry.getUserId());
        row.setModel(aiProperties.getEmbeddingModel());
        row.setVector(toJson(vector));
        row.setTextHash(hash(text));

        // entry_id 是主键:存在即覆盖更新,不存在即插入。
        // 不用 UPDATE ... ON DUPLICATE KEY 是因为要在 MyBatis-Plus 的实体层保持可读。
        if (embeddingMapper.selectById(entry.getId()) == null) {
            embeddingMapper.insert(row);
        } else {
            embeddingMapper.updateById(row);
        }
    }

    /**
     * 参与嵌入的文本:标题 + 地点 + 正文。
     *
     * <p>地点单独拼进去很重要 —— 日记正文常写"这里""那边",
     * 地名往往只在 location_name 字段里,不拼进来搜索"京都"就捞不到。
     */
    private String embedText(Entry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.getTitle() != null && !entry.getTitle().isBlank()) {
            sb.append(entry.getTitle().trim()).append('\n');
        }
        if (entry.getLocationName() != null && !entry.getLocationName().isBlank()) {
            sb.append(entry.getLocationName().trim()).append('\n');
        }
        if (entry.getContent() != null) {
            sb.append(entry.getContent());
        }
        return sb.toString().trim();
    }

    private String toJson(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JVM 必然带 SHA-256,这里只是编译器要求
            throw new IllegalStateException(e);
        }
    }

    /* ---------------------------------------------------------------------
     *  检索
     * ------------------------------------------------------------------- */

    /**
     * 语义搜索:把查询也嵌入一次,与当前用户全部向量算余弦,取 top-N。
     *
     * <p>在内存里算而不是 SQL:个人量级几千篇 × 1024 维,一次排序几毫秒;
     * 塞进 MySQL 反而要解析几千个 JSON 字符串,还占连接。
     *
     * @return entryId → 相似度,已按相似度降序、过滤掉低于阈值的
     */
    @Override
    public List<Scored> search(Long userId, String query) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null || query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = model.embed(query.trim());
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        List<EntryEmbedding> rows = embeddingMapper.selectVectorsByUser(userId, aiProperties.getEmbeddingModel());
        List<Scored> scored = new ArrayList<>(rows.size());
        for (EntryEmbedding row : rows) {
            float[] vector = parseJson(row.getVector());
            if (vector.length != queryVector.length) {
                // 换过模型/维度对不上:跳过而不是抛错,回填后自然恢复
                continue;
            }
            double score = cosine(queryVector, vector);
            if (score >= aiProperties.getMinScore()) {
                scored.add(new Scored(row.getEntryId(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.size() > aiProperties.getSearchLimit()
                ? scored.subList(0, aiProperties.getSearchLimit())
                : scored;
    }

    /** 向量是 "[0.1,0.2,...]" 的 JSON 数组,手工解析比引 Jackson 少一层对象。 */
    private float[] parseJson(String json) {
        if (json == null || json.length() < 2) {
            return new float[0];
        }
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    /** bge-m3 输出已归一化,余弦退化为点积;仍按通式写,换模型也不用改。 */
    private double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
