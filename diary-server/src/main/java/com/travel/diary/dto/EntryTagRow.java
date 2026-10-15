package com.travel.diary.dto;

import lombok.Data;

/** 日记-标签名行,供批量装配。 */
@Data
public class EntryTagRow {

    private Long entryId;
    private Long tagId;
    private String name;
}
