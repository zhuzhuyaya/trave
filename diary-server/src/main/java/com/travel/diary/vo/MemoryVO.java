package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;

/** 「去年的今天」这类回忆卡片。 */
@Data
public class MemoryVO {

    private Long entryId;
    private String title;
    private String excerpt;
    private String locationName;
    private String coverUrl;

    private LocalDate entryDate;

    /** 距今整年数,如 1 表示"一年前"、3 表示"三年前" */
    private int yearsAgo;

    /** 给卡片用的一句话,如 "一年前的今天,你在 大理" */
    private String caption;
}
