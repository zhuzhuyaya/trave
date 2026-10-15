package com.travel.diary.dto;

import lombok.Data;

/** 按年聚合行。字段名与 SQL 列别名一一对应。 */
@Data
public class YearStatRow {

    private Integer entryYear;
    private long entryCount;
    private long dayCount;
    private long placeCount;
}
