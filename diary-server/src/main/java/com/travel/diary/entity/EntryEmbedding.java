package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记的语义向量(bge-m3,1024 维)。
 *
 * <p>vector 存 JSON 数组字符串而非 BLOB:调试时肉眼可读,
 * Java 侧 Jackson 直接反序列化,体积差在这个量级无感。
 * text_hash 是参与嵌入的文本(标题 + 地点 + 正文)的 SHA-256 ——
 * 只改心情、天气、标签、配图时哈希不变,保存与回填都跳过重算。
 */
@Data
@TableName("entry_embeddings")
public class EntryEmbedding {

    /** 即 entry_id:一篇日记最多一条向量,整体覆盖更新。 */
    @TableId(type = IdType.INPUT)
    private Long entryId;

    private Long userId;

    /** 嵌入模型标识;换了模型必须全量重建,写入时附带便于排查。 */
    private String model;

    /** JSON 数组,1024 个 float。 */
    private String vector;

    /** 嵌入时原文的 SHA-256(hex)。 */
    private String textHash;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
