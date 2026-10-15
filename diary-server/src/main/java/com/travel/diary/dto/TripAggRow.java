package com.travel.diary.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 行程维度的聚合行(一条 SQL 一次算出所有行程的统计,避免逐行程查询)。
 *
 * <p>{@code locations} 是用逗号拼接的去重地点名,Java 侧再切成数组 ——
 * 用 GROUP_CONCAT 换掉 N 次查询,在这个数据量下非常划算。
 */
@Data
public class TripAggRow {

    private Long tripId;
    private long entryCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String locations;

    /**
     * 自动兜底用的封面:行程内最新一篇日记的首图。
     *
     * <p>与统计一起在同一条聚合 SQL 里算出来,而不是回头给每段行程单开一次查询。
     * 作者自己设过封面时这个值用不上,由 TripVO.coverUrl 的解析逻辑决定取谁。
     */
    private String fallbackCover;
}
