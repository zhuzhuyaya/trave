package com.travel.diary.dto;

import lombok.Data;

import java.time.LocalDate;

/** 「历史上的今天」行。 */
@Data
public class MemoryRow {

    private Long id;
    private String title;
    private String excerpt;
    private String locationName;
    private LocalDate entryDate;
    private Integer entryYear;
    private String coverUrl;
}
