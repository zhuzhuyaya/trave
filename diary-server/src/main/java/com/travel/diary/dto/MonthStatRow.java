package com.travel.diary.dto;

import lombok.Data;

/** 按月聚合行。字段名与 SQL 列别名一一对应。 */
@Data
public class MonthStatRow {

    private Integer entryYear;
    private Integer entryMonth;
    private long entryCount;
}
