package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 回忆页的统计数据。全部由实时聚合 SQL 算出,没有冗余计数表。 */
@Data
public class StatsVO {

    /** 日记总篇数 */
    private long entryCount;
    /** 行程数 */
    private long tripCount;
    /** 去过的地方数(按 location_name 去重) */
    private long placeCount;
    /** 有记录的天数(按 entry_date 去重) */
    private long dayCount;
    /** 图片总数 */
    private long photoCount;
    /** 覆盖的年份跨度中的年数 */
    private long yearCount;

    /** 最早一篇的日期,展示"从 X 年开始记录" */
    private LocalDate firstDate;

    /** 按年统计,升序 */
    private List<YearStat> yearly;

    /** 按月统计,只含最近 12 个月,升序 */
    private List<MonthStat> monthly;

    @Data
    public static class YearStat {
        private int year;
        private long entryCount;
        private long dayCount;
        private long placeCount;
    }

    @Data
    public static class MonthStat {
        /** 1-12 */
        private int month;
        private int year;
        private long entryCount;
    }
}
