package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.entity.EntryTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 日记-标签关联。
 *
 * <p>没有自增主键,{@code insert} 用 BaseMapper 的通用方法即可;
 * 删除只能按条件删 —— MyBatis-Plus 对无 {@code @TableId} 的实体不支持 {@code deleteById}。
 */
public interface EntryTagMapper extends BaseMapper<EntryTag> {

    @Delete("DELETE FROM entry_tags WHERE entry_id = #{entryId}")
    int deleteByEntryId(@Param("entryId") Long entryId);
}
