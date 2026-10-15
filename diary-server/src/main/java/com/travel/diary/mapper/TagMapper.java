package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.dto.EntryTagRow;
import com.travel.diary.entity.Tag;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface TagMapper extends BaseMapper<Tag> {

    /** 批量取日记的标签名。一次查询覆盖整页,避免逐篇查。 */
    @Select("""
            <script>
            SELECT et.entry_id AS entryId, t.id AS tagId, t.name AS name
              FROM entry_tags et
              JOIN tags t ON t.id = et.tag_id
             WHERE et.entry_id IN
             <foreach collection="entryIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
             ORDER BY t.name
            </script>""")
    List<EntryTagRow> findByEntryIds(@Param("entryIds") List<Long> entryIds);

    /**
     * 标签使用数增减。
     *
     * <p>{@code GREATEST(..., 0)} 兜住并发下的负数 —— 两个请求同时删最后一篇带该标签的日记,
     * use_count 可能从 1 减到 -1,界面上就会出现"-1 篇"这种荒唐数字。
     */
    @Update("UPDATE tags SET use_count = GREATEST(use_count + #{delta}, 0) WHERE id = #{tagId}")
    int addUseCount(@Param("tagId") Long tagId, @Param("delta") int delta);
}
