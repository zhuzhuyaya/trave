package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.entity.EntryEmbedding;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface EntryEmbeddingMapper extends BaseMapper<EntryEmbedding> {

    /** 只取向量列不取整行:向量是几百 KB 的 JSON 文本,esp. 全量加载时省一半带宽。 */
    @Select("""
            SELECT entry_id, vector FROM entry_embeddings
             WHERE user_id = #{userId} AND model = #{model}
            """)
    List<EntryEmbedding> selectVectorsByUser(@Param("userId") Long userId, @Param("model") String model);
}
