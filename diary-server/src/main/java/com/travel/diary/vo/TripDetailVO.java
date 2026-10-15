package com.travel.diary.vo;

import lombok.Data;

import java.util.List;

/** 行程详情:行程本身 + 其下的日记摘要。 */
@Data
public class TripDetailVO {

    private TripVO trip;

    private List<EntrySummaryVO> entries;
}
