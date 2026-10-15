package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.dto.GeoPointRow;
import com.travel.diary.dto.MemoryRow;
import com.travel.diary.dto.MonthStatRow;
import com.travel.diary.dto.TotalsRow;
import com.travel.diary.dto.YearStatRow;
import com.travel.diary.entity.Entry;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface EntryMapper extends BaseMapper<Entry> {

    /**
     * 阅读数 +1。
     *
     * <p>用 UPDATE ... SET x = x + 1 而不是"读出来加一再写回":
     * 后者在并发下会丢更新,而且会把 updated_at 顶到当前时间 ——
     * 进一次详情页就显示"刚刚编辑过",观感很怪。
     */
    @Update("UPDATE entries SET view_count = view_count + 1 WHERE id = #{entryId} AND deleted = 0")
    int incrementViewCount(@Param("entryId") Long entryId);

    /** 删除行程时顺带逻辑删掉它名下的日记。 */
    @Update("UPDATE entries SET deleted = 1 WHERE trip_id = #{tripId} AND user_id = #{userId} AND deleted = 0")
    int softDeleteByTrip(@Param("tripId") Long tripId, @Param("userId") Long userId);

    /**
     * 某用户有坐标的日记,按日期升序。
     *
     * <p>JOIN 出 trip_title 而不是取回后再逐条查行程表 ——
     * 地图页可能一次拉几百个点,逐个补标题就是几百次查询。
     */
    @Select("""
            SELECT e.id            AS id,
                   e.title         AS title,
                   e.latitude      AS latitude,
                   e.longitude     AS longitude,
                   e.entry_date    AS entryDate,
                   e.location_name AS locationName,
                   e.trip_id       AS tripId,
                   t.title         AS tripTitle
              FROM entries e
              LEFT JOIN trips t ON t.id = e.trip_id
             WHERE e.user_id = #{userId}
               AND e.deleted = 0
               AND e.latitude IS NOT NULL
               AND e.longitude IS NOT NULL
             ORDER BY e.entry_date ASC, e.id ASC
            """)
    List<GeoPointRow> findGeoPoints(@Param("userId") Long userId);

    /**
     * 统计页的全部标量指标,一条 SQL 出。
     *
     * <p>子查询里各自带 user_id 与 deleted 条件,而不是在外层 JOIN 后再过滤 ——
     * 后者会让"日记数"与"图片数"互相乘出笛卡尔积,数字凭空翻好几倍。
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM entries WHERE user_id = #{userId} AND deleted = 0)                       AS entryCount,
              (SELECT COUNT(DISTINCT entry_date) FROM entries WHERE user_id = #{userId} AND deleted = 0)     AS dayCount,
              (SELECT COUNT(DISTINCT location_name) FROM entries
                WHERE user_id = #{userId} AND deleted = 0 AND location_name IS NOT NULL
                  AND location_name <> '')                                                                   AS placeCount,
              (SELECT COUNT(*) FROM entry_photos WHERE user_id = #{userId} AND deleted = 0)                   AS photoCount,
              (SELECT COUNT(*) FROM trips WHERE user_id = #{userId})                                          AS tripCount,
              (SELECT MIN(entry_date) FROM entries WHERE user_id = #{userId} AND deleted = 0)                 AS firstDate
            """)
    TotalsRow totals(@Param("userId") Long userId);

    /** 按年聚合。年份来自 entry_date 而非 created_at —— 补写去年日记时统计应落在去年。 */
    @Select("""
            SELECT YEAR(entry_date)                    AS entryYear,
                   COUNT(*)                            AS entryCount,
                   COUNT(DISTINCT entry_date)          AS dayCount,
                   COUNT(DISTINCT location_name)       AS placeCount
              FROM entries
             WHERE user_id = #{userId} AND deleted = 0
             GROUP BY YEAR(entry_date)
             ORDER BY entryYear ASC
            """)
    List<YearStatRow> statsByYear(@Param("userId") Long userId);

    /** 按月聚合,只取最近 12 个月。 */
    @Select("""
            SELECT YEAR(entry_date)  AS entryYear,
                   MONTH(entry_date) AS entryMonth,
                   COUNT(*)          AS entryCount
              FROM entries
             WHERE user_id = #{userId}
               AND deleted = 0
               AND entry_date >= #{since}
             GROUP BY YEAR(entry_date), MONTH(entry_date)
             ORDER BY entryYear ASC, entryMonth ASC
            """)
    List<MonthStatRow> statsByMonth(@Param("userId") Long userId, @Param("since") LocalDate since);

    /**
     * 「历史上的今天」:同月同日、但不是今天的日记。
     *
     * <p>用 MONTH()/DAY() 而不是精确日期匹配,才能真正捞到往年的同一天;
     * 用 adddate 减年份也可以,但 2 月 29 日在平年不存在,反而要额外兜底。
     */
    @Select("""
            SELECT e.id, e.title, e.excerpt, e.location_name AS locationName,
                   e.entry_date AS entryDate, YEAR(e.entry_date) AS entryYear,
                   (SELECT p.url FROM entry_photos p
                     WHERE p.entry_id = e.id AND p.deleted = 0
                     ORDER BY p.sort_order, p.id LIMIT 1) AS coverUrl
              FROM entries e
             WHERE e.user_id = #{userId}
               AND e.deleted = 0
               AND MONTH(e.entry_date) = #{month}
               AND DAY(e.entry_date) = #{day}
               AND YEAR(e.entry_date) < #{year}
             ORDER BY e.entry_date DESC
             LIMIT 20
            """)
    List<MemoryRow> findMemories(@Param("userId") Long userId,
                                 @Param("year") int year,
                                 @Param("month") int month,
                                 @Param("day") int day);
}
