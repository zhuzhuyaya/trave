package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.dto.TripAggRow;
import com.travel.diary.entity.Trip;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TripMapper extends BaseMapper<Trip> {

    /**
     * 一次算出某用户所有行程的日记数、起止日期与去重地点。
     *
     * <p>GROUP_CONCAT 的默认长度上限是 1024 字节,超出会被静默截断 ——
     * 地点名都很短,单条行程的日记数也有限,够用;真截断了也只是副标题少几个名字。
     */
    @Select("""
            SELECT t.id                AS tripId,
                   COUNT(e.id)         AS entryCount,
                   MIN(e.entry_date)   AS startDate,
                   MAX(e.entry_date)   AS endDate,
                   GROUP_CONCAT(DISTINCT e.location_name ORDER BY e.entry_date SEPARATOR ',') AS locations,
                   (SELECT p.url
                      FROM entries e2
                      JOIN entry_photos p ON p.entry_id = e2.id AND p.deleted = 0
                     WHERE e2.trip_id = t.id AND e2.deleted = 0
                     ORDER BY e2.entry_date DESC, e2.id DESC, p.sort_order, p.id
                     LIMIT 1)         AS fallbackCover
              FROM trips t
              LEFT JOIN entries e ON e.trip_id = t.id AND e.deleted = 0 AND e.location_name IS NOT NULL
             WHERE t.user_id = #{userId}
             GROUP BY t.id
            """)
    List<TripAggRow> aggregateByUser(@Param("userId") Long userId);

    /** 单个行程的聚合,详情页用。 */
    @Select("""
            SELECT t.id                AS tripId,
                   COUNT(e.id)         AS entryCount,
                   MIN(e.entry_date)   AS startDate,
                   MAX(e.entry_date)   AS endDate,
                   GROUP_CONCAT(DISTINCT e.location_name ORDER BY e.entry_date SEPARATOR ',') AS locations,
                   (SELECT p.url
                      FROM entries e2
                      JOIN entry_photos p ON p.entry_id = e2.id AND p.deleted = 0
                     WHERE e2.trip_id = t.id AND e2.deleted = 0
                     ORDER BY e2.entry_date DESC, e2.id DESC, p.sort_order, p.id
                     LIMIT 1)         AS fallbackCover
              FROM trips t
              LEFT JOIN entries e ON e.trip_id = t.id AND e.deleted = 0 AND e.location_name IS NOT NULL
             WHERE t.id = #{tripId}
             GROUP BY t.id
            """)
    TripAggRow aggregateOne(@Param("tripId") Long tripId);
}
