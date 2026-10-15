package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 日记详情。 */
@Data
public class EntryDetailVO {

    private Long id;
    private String title;
    /** Markdown 原文,由前端渲染并做 XSS 清洗 */
    private String content;

    private LocalDate entryDate;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private String weather;
    private String mood;

    private List<PhotoVO> photos;
    private List<String> tags;

    private Long tripId;
    private String tripTitle;

    private Integer viewCount;

    /** 当前登录者能否编辑(作者本人或管理员),前端据此决定显示不显示编辑按钮 */
    private boolean canEdit;

    /** 当前登录者是否收藏过 */
    private boolean bookmarked;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
