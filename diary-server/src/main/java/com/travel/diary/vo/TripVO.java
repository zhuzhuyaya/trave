package com.travel.diary.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 行程卡片。列表页用这个,详情页在它基础上多带日记列表。 */
@Data
public class TripVO {

    private Long id;
    private String title;
    private String description;

    /**
     * 卡片/详情页实际该显示的那张图 —— 已经解析好的结果,不是原始存储值。
     *
     * <p>优先级:自定义封面 → 行程内最新一篇日记的首图 → null。
     * 为 null 时前端渲染按 id 生成的渐变占位块。
     */
    private String coverUrl;

    /**
     * 作者自己指定的封面图 id;没设过就是 null(此时 coverUrl 来自自动兜底)。
     *
     * <p>编辑面板要靠它区分"这封面是我挑的"和"这是系统兜底出来的" ——
     * 否则用户点「移除封面」会发现画面没变,像是坏了。
     */
    private Long coverPhotoId;

    /** PUBLIC / PRIVATE */
    private String visibility;
    private boolean archived;

    private Integer entryCount;
    /** 行程内最早的日记日期,展示"起" */
    private java.time.LocalDate startDate;
    /** 行程内最晚的日记日期,展示"止" */
    private java.time.LocalDate endDate;
    /** 去重后的地点,用于卡片副标题("大理 · 洱海 · 沙溪") */
    private String locationSummary;

    private LocalDateTime createdAt;

    /**
     * 当前查看者能不能改这段行程(作者本人或管理员)。
     *
     * <p>公开行程对所有人可读,但只有作者能改 —— 前端得靠这个字段决定
     * 「编辑行程」「写日记」「删除」这几个入口显不显示。
     * 光靠后端拦会让人点了才发现被拒,那是很差的反馈。
     */
    private boolean canEdit;
}
