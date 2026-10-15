package com.travel.diary.dto;

import lombok.Data;

import java.time.LocalDate;

/** 简单聚合行:计数 + 最早日期,一次查完整个统计页的标量指标。 */
@Data
public class TotalsRow {

    private long entryCount;
    private long dayCount;
    private long placeCount;
    private long photoCount;
    private long tripCount;
    private LocalDate firstDate;
}
