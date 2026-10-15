package com.travel.diary.dto;

import lombok.Data;

import java.util.List;

/** 新建 / 编辑日记共用。 */
@Data
public class EntryRequest {

    @jakarta.validation.constraints.NotBlank(message = "标题不能为空")
    @jakarta.validation.constraints.Size(min = 1, max = 120, message = "标题需在 1-120 字之间")
    private String title;

    @jakarta.validation.constraints.NotBlank(message = "正文不能为空")
    @jakarta.validation.constraints.Size(max = 50000, message = "正文过长,请控制在 5 万字以内")
    private String content;

    /** 日记所属日期,格式 yyyy-MM-dd。留空由服务端取当天。 */
    private String entryDate;

    /** 所属行程;留空表示未归类 */
    private Long tripId;

    private String locationName;

    /**
     * 经纬度。两个都提供才写库 —— 只给纬度谈不上是一个地点,
     * 与其半信半疑地存下来,不如整对丢弃。
     */
    private Double latitude;
    private Double longitude;

    private String weather;
    private String mood;

    @jakarta.validation.constraints.Size(max = 5, message = "最多只能挂 5 个标签")
    private List<@jakarta.validation.constraints.Size(max = 12, message = "单个标签最长 12 个字") String> tags;

    /** 图片地址列表(由 /photos 上传后得到),顺序即展示顺序 */
    @jakarta.validation.constraints.Size(max = 18, message = "单篇日记最多 18 张图片")
    private List<String> photoUrls;
}
