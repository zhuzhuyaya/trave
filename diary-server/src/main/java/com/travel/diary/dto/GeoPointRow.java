package com.travel.diary.dto;

import lombok.Data;

import java.time.LocalDate;

/** 地图坐标行。只取经纬度都非空的记录。 */
@Data
public class GeoPointRow {

    private Long id;
    private String title;
    private Double latitude;
    private Double longitude;
    private LocalDate entryDate;
    private String locationName;
    private Long tripId;
    private String tripTitle;
    private Long coverPhotoId;
}
