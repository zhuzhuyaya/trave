package com.travel.diary.vo;

import lombok.Data;

/** 单张图片。 */
@Data
public class PhotoVO {

    private Long id;
    private String url;
    private String thumbUrl;
    private Integer width;
    private Integer height;
    private Integer sortOrder;
}
