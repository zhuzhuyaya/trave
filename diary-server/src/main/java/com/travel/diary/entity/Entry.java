package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("entries")
public class Entry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 可为空:允许写下"还没归类到某段行程"的日记 */
    private Long tripId;

    private Long userId;

    private String title;

    /** Markdown 原文,由前端渲染并做 XSS 清洗 */
    private String content;

    /** 剥离标记后的纯文本摘要,列表页用 */
    private String excerpt;

    /** 日记所属日期。与 createdAt 不同 —— 补写昨天的日记时日期应该是昨天。 */
    private LocalDate entryDate;

    private String locationName;

    /** 纬度。可为空:不是每篇日记都值得打点。 */
    private BigDecimal latitude;

    /** 经度。可为空。 */
    private BigDecimal longitude;

    private String weather;

    private String mood;

    private Integer viewCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 前后端统一用这个判断"能不能画在地图上",避免两处各写一遍空值判断。 */
    public boolean hasCoordinate() {
        return latitude != null && longitude != null;
    }
}
