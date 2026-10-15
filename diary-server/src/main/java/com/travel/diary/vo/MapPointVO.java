package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;

/** 地图上的一个足迹点。 */
@Data
public class MapPointVO {

    private Long id;
    private String title;
    private double latitude;
    private double longitude;
    private LocalDate entryDate;
    private String locationName;
    /** 圆形标记里显示的缩略图;为空时前端画成带首字母的纯色圆 */
    private String coverUrl;

    private Long tripId;
    private String tripTitle;
}
