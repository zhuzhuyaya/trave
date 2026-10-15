package com.travel.diary.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建 / 编辑行程共用。 */
@Data
public class TripRequest {

    /**
     * coverPhotoId 传这个值表示「清掉自定义封面」,退回自动兜底。
     *
     * <p>需要一个哨兵是因为本类是 patch 式的:字段为 null 一律当"没传,别动"。
     * 少了这个 0,"把封面去掉"就表达不出来了 —— null 已经被"不动"占用。
     */
    public static final long COVER_NONE = 0L;

    @Size(max = 60, message = "行程名最多 60 个字")
    private String title;

    @Size(max = 255, message = "简介最多 255 个字")
    private String description;

    /** PUBLIC / PRIVATE,留空按 PRIVATE 处理 */
    private String visibility;

    private Boolean archived;

    /**
     * 自定义封面用的图片 id(来自 {@code POST /api/photos} 的返回值)。
     *
     * <ul>
     *   <li>不传 / null —— 不动这一项</li>
     *   <li>{@link #COVER_NONE} —— 清掉自定义封面</li>
     *   <li>大于 0 —— 设为这张图,必须是本人上传的</li>
     * </ul>
     */
    private Long coverPhotoId;
}
