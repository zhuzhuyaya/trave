package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行程 —— 把若干篇日记归到一起,语义上接近"一本相册"或"一次出行"。
 */
@Data
@TableName("trips")
public class Trip {

    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    /** 封面附件 id;为空时由 ViewAssembler 取行程内最新一篇日记的首图 */
    private Long coverPhotoId;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private Integer isArchived;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isPubliclyVisible() {
        return VISIBILITY_PUBLIC.equals(visibility);
    }
}
