package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.dto.EntryPhotoRow;
import com.travel.diary.entity.EntryPhoto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface EntryPhotoMapper extends BaseMapper<EntryPhoto> {

    /**
     * 列出指定日记的所有图片,并标出每篇的第一张。
     *
     * <p>{@code ROW_NUMBER() OVER (PARTITION BY entry_id ORDER BY sort_order, id)}
     * 是 MySQL 8 的窗口函数,一次查询同时拿到"每篇的封面"和"全部图片",
     * 不必再为封面单独跑一次 {@code LIMIT 1} 子查询。
     *
     * <p>这条 SQL 也是列表页消除 N+1 的关键:一页 10 篇日记只查 1 次,
     * 而不是 10 次。
     */
    @Select("""
            <script>
            SELECT p.entry_id AS entryId,
                   p.id       AS photoId,
                   p.url      AS url,
                   p.sort_order AS sortOrder,
                   CASE WHEN p.rn = 1 THEN 1 ELSE 0 END AS isCover
              FROM (
                    SELECT ep.id,
                           ep.entry_id,
                           ep.url,
                           ep.sort_order,
                           ROW_NUMBER() OVER (PARTITION BY ep.entry_id ORDER BY ep.sort_order, ep.id) AS rn
                      FROM entry_photos ep
                     WHERE ep.deleted = 0
                       AND ep.entry_id IN
                     <foreach collection="entryIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
                   ) p
             ORDER BY p.entry_id, p.sort_order, p.id
            </script>""")
    List<EntryPhotoRow> findByEntryIds(@Param("entryIds") List<Long> entryIds);
}
