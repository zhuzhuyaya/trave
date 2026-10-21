package com.travel.diary.vo;

import lombok.Data;

import java.util.List;

/**
 * 收藏页的一屏数据:分页卡片 + 可选的标签筛选项。
 *
 * <p>不用 {@link PageResult}:收藏页还要把"这些收藏里出现过的标签"一并带回去,
 * 塞进 {@code PageResult} 的泛型里要么再造一个 VO 列表要么乱用字段,不如明说。
 */
@Data
public class BookmarkPageVO {

    private List<EntrySummaryVO> list;

    private long total;

    /** 当前筛选下的可用标签(按收藏中出现次数降序),给筛选 chips 用。 */
    private List<BookmarkTagVO> tags;

    /** 标签筛选是否生效中。前端拿它决定"清除筛选"按钮显不显示。 */
    private boolean filtered;
}
