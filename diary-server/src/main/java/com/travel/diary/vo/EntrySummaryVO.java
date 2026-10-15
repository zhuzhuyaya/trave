package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 日记列表卡片。列表页只渲染这些字段,不传正文。 */
@Data
public class EntrySummaryVO {

    private Long id;
    private String title;
    /** 纯文本摘要,已截断 */
    private String excerpt;
    private LocalDate entryDate;

    private String locationName;

    /** 地图页用;两者都非空时前端才画点 */
    private Double latitude;
    private Double longitude;

    private String weather;
    private String mood;

    private Integer photoCount;
    /** 封面图(首图);为空时前端渲染渐变占位块 */
    private String coverUrl;

    private List<String> tags;

    private Long tripId;
    private String tripTitle;

    /** 当前登录者能否编辑。列表页据此决定长按菜单里显不显示"删除" */
    private boolean canEdit;
}
