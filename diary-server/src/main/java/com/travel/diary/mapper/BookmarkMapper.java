package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.entity.Bookmark;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏。
 *
 * <p>开关收藏走 BaseMapper 的通用方法 —— 按
 * {@code (entry_id, user_id)} 条件查一次、插一条或删一条,唯一索引负责兜住并发重复。
 */
public interface BookmarkMapper extends BaseMapper<Bookmark> {

    /**
     * 收藏页的候选 id:该用户收藏的、且此刻仍真实可读的日记,新→旧。
     *
     * <p>"仍真实可读"的四件事必须在 SQL 里做完 ——
     * 日记没有逻辑删、日记没有被删掉(收藏表只删关联不回滚收藏,反之亦然)、
     * 行程可见性允许阅读。收藏了别人的公开行程里的日记,作者后来
     * 把行程转私密,这条收藏就该从列表里消失,而不是点进去报 404。
     * (无行程挂载的日记只可能被作者本人读到:LEFT JOIN 出来的
     *  t.visibility 必为 null,唯一的分支就是 e.user_id = userId。)
     *
     * <p>标签筛选在 Java 侧做而不是拼进这条 SQL:参数多了整条 SQL 难读,
     * 且收藏页单用户数据量小(几十条),内存过滤的成本可以忽略。
     */
    @Select("""
            SELECT b.id
              FROM bookmarks b
              JOIN entries e ON e.id = b.entry_id AND e.deleted = 0
              LEFT JOIN trips t ON t.id = e.trip_id
             WHERE b.user_id = #{userId}
               AND (e.user_id = #{userId} OR t.visibility = 'PUBLIC')
             ORDER BY b.id DESC
            """)
    List<Long> findReadableEntryIds(@Param("userId") Long userId);
}
