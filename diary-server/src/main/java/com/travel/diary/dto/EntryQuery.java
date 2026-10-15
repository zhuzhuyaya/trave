package com.travel.diary.dto;

import lombok.Data;

/**
 * 日记列表的查询条件。
 *
 * <p>注意这里用 {@code @Data} 而不是 {@code record} —— Spring MVC 从 query string
 * 绑定需要一个无参构造 + setter,record 绑不上。
 */
@Data
public class EntryQuery {

    private Long tripId;

    private String tag;

    /** 关键词,匹配标题、摘要、地点名 */
    private String keyword;

    private Integer year;

    /** 1-12;单独给 month 而不给 year 时按"最近的那一年"处理 */
    private Integer month;

    /** latest(默认,新→旧) / oldest(旧→新) */
    private String sort = "latest";

    private Integer page;

    private Integer size;
}
