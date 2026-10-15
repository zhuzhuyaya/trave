package com.travel.diary.dto;

import lombok.Data;

/**
 * 图片行(批量装配用)。
 *
 * <p>{@code coverPhotoId} 由窗口函数标出每篇日记的第一张图,
 * 于是列表页的封面不需要再逐条查一次。
 */
@Data
public class EntryPhotoRow {

    private Long entryId;
    private Long photoId;
    private String url;
    private Integer sortOrder;
    /** 1 表示这是该日记 sort_order 最小的那张 */
    private Integer isCover;
}
