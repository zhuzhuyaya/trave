package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 一次出行的足迹路线,供地图连线与侧栏分组展示。 */
@Data
public class FootprintVO {

    /** 归属行程 id;未归类的日记为 null */
    private Long tripId;
    private String tripTitle;

    private int pointCount;
    private LocalDate startDate;
    private LocalDate endDate;

    /** 按日期升序排好的点,前端直接连成折线 */
    private List<MapPointVO> points;
}
